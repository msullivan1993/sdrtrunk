/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * ****************************************************************************
 */

package io.github.dsheirer.module.decode.nxdn.layer1;

import io.github.dsheirer.dsp.symbol.Dibit;
import java.text.DecimalFormat;
import java.util.Arrays;
import org.apache.commons.math3.stat.descriptive.moment.Variance;

/**
 * Adaptive equalizer for C4FM symbols.
 */
public class C4FMEqualizer
{
    private static final DecimalFormat DF = new DecimalFormat("0.0000");
    //Maximum possible error is PI/4 = 0.78, but we clip it to less than half as the allowable error for tap updates
    private static final float MAX_SYMBOL_ERROR = 0.3f;
    private static final double TRAINING_IMPROVEMENT_GOAL_PER_ITERATION = 0.001f;
    private static final double TRAINING_CONVERGENCE_THRESHOLD = 0.16f;
    private static final double TRAINING_SKIP_THRESHOLD = 0.05;
    private static final int TRAINING_ITERATIONS_MAX = 50;
    private final Variance mVariance = new Variance();
    private final float[] mBuffer;
    private final float[] mReversedSyncSymbols;
    private final int mDelaySymbolCount;
    private boolean mDynamicTapUpdates = false;
    private boolean mEnabled = false;
    private boolean mSyncDetected = false;
    private float[] mTaps;
    private float[] mTapsTrainingSnapshot;
    private int mCenterTap = 0;
    private int mTrainingSymbolsRemaining = 0;

    /**
     * Constructs an instance
     * @param syncSymbols used to train the equalizer on sync detections
     * @param symbolLength for the equalizer, odd length.  Note: if the argument is not odd valued,
     * the symbol count will be increased to make it odd length.
     */
    public C4FMEqualizer(float[] syncSymbols, int symbolLength)
    {
        //Store the sync pattern in reversed order
        mReversedSyncSymbols = new float[syncSymbols.length];
        for(int x = 0; x < syncSymbols.length; x++)
        {
            mReversedSyncSymbols[syncSymbols.length - x - 1] = syncSymbols[x];
        }

        //Make the symbol count odd length for equal delay on either side of the center tap
        symbolLength += (symbolLength % 2 == 0) ? 1 : 0;
        mCenterTap = symbolLength; //This works since we're going to double the buffer length
        mDelaySymbolCount = symbolLength / 2; //Delay until new symbol is aligned to center tap
        mTaps = new float[symbolLength * 2];
        mTaps[mCenterTap] = 1.0f;
        mTapsTrainingSnapshot = Arrays.copyOf(mTaps, mTaps.length);
        int bufferLength = mReversedSyncSymbols.length * 2 + mTaps.length - 2;
        mBuffer = new float[bufferLength];
    }

    /**
     * Description of the current tap values
     */
    public String getTapsDescription()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<html>EQ Taps<br>");
        for(int i = 0; i < mTaps.length; i++)
        {
            sb.append(i).append(": ").append(DF.format(mTaps[i])).append("<br>");
        }

        return sb.toString();
    }

    /**
     * Symbol processing delay.
     * @return processing delay in symbols.
     */
    public int getDelaySymbolCount()
    {
        return mDelaySymbolCount;
    }

    /**
     * Length of the equalizer in symbols.  Note: the internal tap count is twice as long since this
     * is a fractional (x2) equalizer.
     * @return length in symbols.
     */
    public int getLength()
    {
        return mTaps.length / 2;
    }

    /**
     * Enable the equalizer when a valid sync pattern is detected
     */
    public void enable()
    {
        mEnabled = true;
    }

    /**
     * Indicates the enabled state.
     * @return enabled state
     */
    public boolean isEnabled()
    {
        return mEnabled;
    }

    /**
     * Disable the equalizer when the signal is lost so that noise doesn't contaminate the equalizer taps.
     */
    public void disable()
    {
        mEnabled = false;
    }

    /**
     * Notifies the equalizer that a sync pattern is detected and queues a training sequence once a couple more
     * symbols arrive once the equalizer delay line has aligned to output the sync pattern.
     */
    public void syncDetected()
    {
        mSyncDetected = true;
        mTrainingSymbolsRemaining = mDelaySymbolCount;
    }

    /**
     * Indicates that the sync patter is lost and the equalizer should reset.
     */
    public void syncLost()
    {
        reset();
        disable();
    }

    /*
     * Resets the equalizer taps
     */
    public void reset()
    {
        Arrays.fill(mTaps, 0f);
        mTaps[mCenterTap] = 1.0f;
        mTapsTrainingSnapshot = Arrays.copyOf(mTaps, mTaps.length);
        mVariance.clear();
    }

    /**
     * Process the samples and produce a delayed, equalized soft symbol ready for symbol decision.  Updates the
     * equalizer taps to correct for the error in the equalized sample relative to the hard symbol decision.
     *
     * @param fractional sample that falls halfway between symbols
     * @param symbol to equalize
     * @return equalized symbol.
     */
    public float process(float fractional, float symbol)
    {
        //Shift buffer samples to the right two places and add new sample and symbol.
        System.arraycopy(mBuffer, 0, mBuffer, 2, mBuffer.length - 2);
        mBuffer[0] = fractional;
        mBuffer[1] = symbol;

        //Since there's a buffer delay, count down from sync detect until the pattern is aligned with the buffer
        // contents and we can start training
        if(mSyncDetected)
        {
            if(--mTrainingSymbolsRemaining <= 0)
            {
                train();
            }
        }

        float equalized = 0f;

        if(mEnabled)
        {
            int index;

            for(index = 0; index < mTaps.length; index++)
            {
                equalized += mTaps[index] * mBuffer[index];
            }

            if(mDynamicTapUpdates)
            {
                float error = Dibit.getError(equalized);
                error = Math.clamp(error, -MAX_SYMBOL_ERROR, MAX_SYMBOL_ERROR);

                //Step size (mu) in range 0.0001 to 0.01
                float stepSize = 0.01f;

                for(index = 0; index < mTaps.length; index++)
                {
                    mTaps[index] += stepSize * error * mBuffer[index];
                }
            }

            //Track the residual error that remains after equalization
            mVariance.increment(Dibit.getError(equalized));
        }
        else
        {
            //Return the uncorrected, delayed symbol when we're disabled
            equalized = mBuffer[mCenterTap];
        }

        return equalized;
    }

    /**
     * Trains the equalizer against the buffered detected sync pattern.  Trains against the sync pattern sequence
     * multiple times while the per-iteration improvement continues to become smaller and stops training once the
     * improvement falls below a threshold goal or exceeds a maximum iterations.  If the training converges to
     * below a threshold, we snapshot and use the trained taps, otherwise fallback to the previous training snapshot.
     */
    private void train()
    {
        mSyncDetected = false;

        //Ensure we've collected enough variance samples in the buffer and we have sufficient variance
        if(mVariance.getN() <= mDelaySymbolCount || mVariance.getResult() > TRAINING_SKIP_THRESHOLD)
        {
            //If the current variance tracked since the last training session using dynamic tap adjustments at each
            // symbol is excessive, fallback to the most recent training snapshot as a better starting point
            if(mVariance.getResult() > 0.225)
            {
                mTaps = Arrays.copyOf(mTapsTrainingSnapshot, mTapsTrainingSnapshot.length);
            }

            float accumulator, error;
            int symbolIndex, index, trainingIterationCount = 0;
            double improvement, previousVariance;
            Variance variance = new Variance();

            do
            {
                previousVariance = variance.getN() == 0 ? 20.0 : variance.getResult();
                variance.clear();

                for(symbolIndex = 0; symbolIndex < mReversedSyncSymbols.length; symbolIndex++)
                {
                    accumulator = 0.0f;
                    for(index = 0; index < mTaps.length; index++)
                    {
                        accumulator += mTaps[index] * mBuffer[index + (2 * symbolIndex)];
                    }

                    error = mReversedSyncSymbols[symbolIndex] - accumulator;
                    variance.increment(error);

                    //Step size (mu) in range 0.0001 to 0.01
                    float stepSize = 0.01f;

                    for(index = 0; index < mTaps.length; index++)
                    {
                        mTaps[index] += error * stepSize * mBuffer[index + (2 * symbolIndex)];
                    }
                }

                trainingIterationCount++;
                improvement = previousVariance - variance.getResult();
            }
            while(trainingIterationCount < TRAINING_ITERATIONS_MAX && improvement > TRAINING_IMPROVEMENT_GOAL_PER_ITERATION);

//            System.out.println("Training Iterations:" + trainingIterationCount + " End Variance:" + DF.format(variance.getResult()));

            //Use and snapshot the trained taps if they converge, otherwise revert to the previous training snapshot and
            //disable dynamic tap updates until the signal becomes less noisy
            if(variance.getResult() < TRAINING_CONVERGENCE_THRESHOLD)
            {
                mTapsTrainingSnapshot = Arrays.copyOf(mTaps, mTaps.length);
                mDynamicTapUpdates = true;
            }
            else
            {
                mTaps = Arrays.copyOf(mTapsTrainingSnapshot, mTapsTrainingSnapshot.length);
                mDynamicTapUpdates = false;
            }
        }
        else if(mVariance.getN() >= mDelaySymbolCount && mVariance.getResult() < TRAINING_SKIP_THRESHOLD)
        {
            //Snapshot the current taps as the training sequence
            mTapsTrainingSnapshot = Arrays.copyOf(mTaps, mTaps.length);
            mDynamicTapUpdates = true;
        }

        mVariance.clear();
    }
}
