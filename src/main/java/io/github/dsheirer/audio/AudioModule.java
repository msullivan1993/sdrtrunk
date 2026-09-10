/*
 * *****************************************************************************
 * Copyright (C) 2014-2023 Dennis Sheirer
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
package io.github.dsheirer.audio;

import io.github.dsheirer.alias.AliasList;
import io.github.dsheirer.audio.squelch.ISquelchStateListener;
import io.github.dsheirer.audio.squelch.SquelchState;
import io.github.dsheirer.audio.squelch.SquelchStateEvent;
import io.github.dsheirer.dsp.filter.FilterFactory;
import io.github.dsheirer.dsp.filter.design.FilterDesignException;
import io.github.dsheirer.dsp.filter.fir.FIRFilterSpecification;
import io.github.dsheirer.dsp.filter.fir.real.IRealFilter;
import io.github.dsheirer.dsp.filter.fir.remez.RemezFIRFilterDesigner;
import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.sample.real.IRealBufferListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Provides packaging of demodulated audio sample buffers into audio segments for broadcast to registered listeners.
 * Includes audio packet metadata in constructed audio segments.
 *
 * Incorporates audio squelch state listener to control if audio packets are broadcast or ignored.
 */
public class AudioModule extends AbstractAudioModule implements ISquelchStateListener, IRealBufferListener,
    Listener<float[]>
{
    private static final Logger mLog = LoggerFactory.getLogger(AudioModule.class);
    private static final int AUDIO_SAMPLE_RATE = 8000;
    private List<AbstractAudioFilter>mAudioFilters;
    private final SquelchStateListener mSquelchStateListener = new SquelchStateListener();
    private SquelchState mSquelchState = SquelchState.SQUELCH;
    private final int mSquelchDelayTimeMs;
    private final boolean mSquelchDelayRemoveSilence;
    private ScheduledExecutorService mDelayExecutor;
    private ScheduledFuture<?> mPendingClose;

    /**
     * Creates an Audio Module.
     *
     * @param aliasList for aliasing identifiers
     * @param timeslot for this audio module
     * @param maxAudioSegmentLength in milliseconds
     * @param audioFilters a list of audio filters to apply to the incoming audio samples
     */
    public AudioModule(AliasList aliasList, int timeslot, long maxAudioSegmentLength, List<AbstractAudioFilter> audioFilters)
    {
        this(aliasList, timeslot, maxAudioSegmentLength, audioFilters, 0, true);
    }

    /**
     * Creates an Audio Module with squelch delay support.
     *
     * @param aliasList for aliasing identifiers
     * @param timeslot for this audio module
     * @param maxAudioSegmentLength in milliseconds
     * @param audioFilters a list of audio filters to apply to the incoming audio samples
     * @param squelchDelayTimeMs delay in milliseconds before closing the audio segment after squelch closes; 0 disables
     * @param squelchDelayRemoveSilence true to drop the delay-window silence (continuous clip), false to preserve it
     */
    public AudioModule(AliasList aliasList, int timeslot, long maxAudioSegmentLength, List<AbstractAudioFilter> audioFilters,
        int squelchDelayTimeMs, boolean squelchDelayRemoveSilence)
    {
        super(aliasList, timeslot, maxAudioSegmentLength);
        mAudioFilters = audioFilters;
        mSquelchDelayTimeMs = squelchDelayTimeMs;
        mSquelchDelayRemoveSilence = squelchDelayRemoveSilence;

        if(mSquelchDelayTimeMs > 0)
        {
            mDelayExecutor = Executors.newSingleThreadScheduledExecutor();
        }
    }

    /**
     * Creates an Audio Module.
     * @param aliasList for aliasing identifiers
     * @param audioFilters a list of audio filters to apply to the incoming audio samples
     */
    public AudioModule(AliasList aliasList,  List<AbstractAudioFilter> audioFilters)
    {
        super(aliasList);
        mAudioFilters = audioFilters;
        mSquelchDelayTimeMs = 0;
        mSquelchDelayRemoveSilence = true;
    }

    @Override
    protected int getTimeslot()
    {
        return 0;
    }

    @Override
    public void reset()
    {
        getIdentifierCollection().clear();
    }

    @Override
    public void start()
    {
    }

    @Override
    public void stop()
    {
        //Cancel any pending delayed close and shut down this module's delay scheduler so it cannot leak a thread.
        if(mPendingClose != null && !mPendingClose.isDone())
        {
            mPendingClose.cancel(false);
        }

        if(mDelayExecutor != null)
        {
            mDelayExecutor.shutdownNow();
        }

        super.stop();
    }

    @Override
    public Listener<SquelchStateEvent> getSquelchStateListener()
    {
        return mSquelchStateListener;
    }

    @Override
    public void receive(float[] audioBuffer)
    {
        if(mSquelchState == SquelchState.UNSQUELCH)
        {
            // apply audio filters if any (i.e. deemphasis, high pass, etc.)
            if(!mAudioFilters.isEmpty())
            {
                for(AbstractAudioFilter filter : mAudioFilters)
                {
                    audioBuffer = filter.filter(audioBuffer);
                }
            }

            addAudio(audioBuffer);
        }
    }

    @Override
    public Listener<float[]> getBufferListener()
    {
        //Redirect received reusable buffers to the receive(buffer) method
        return this;
    }

    /**
     * Adds the specified duration of silence to the current audio segment, in 100ms chunks at the 8kHz audio rate.
     * Used to preserve inter-transmission timing when the squelch delay window is configured to keep silence.
     * @param durationMs duration of silence to add, in milliseconds
     */
    private void addSilence(int durationMs)
    {
        //800 samples == 100ms at 8kHz.  The buffer is never mutated, so it is safe to reuse across chunks.
        int chunkSize = 800;
        float[] silenceChunk = new float[chunkSize];

        int remainingMs = durationMs;
        while(remainingMs >= 100)
        {
            addAudio(silenceChunk);
            remainingMs -= 100;
        }

        if(remainingMs > 0)
        {
            int remainingSamples = (AUDIO_SAMPLE_RATE * remainingMs) / 1000;
            addAudio(new float[remainingSamples]);
        }
    }

    /**
     * Wrapper for squelch state listener
     */
    public class SquelchStateListener implements Listener<SquelchStateEvent>
    {
        private long mSquelchClosedTimestamp = 0;

        @Override
        public void receive(SquelchStateEvent event)
        {
            SquelchState squelchState = event.getSquelchState();

            if(mSquelchState != squelchState)
            {
                mSquelchState = squelchState;

                if(mSquelchState == SquelchState.SQUELCH)
                {
                    //Record when squelch closed so we can optionally back-fill silence if it reopens in the window.
                    mSquelchClosedTimestamp = System.currentTimeMillis();

                    //When a delay is configured, defer the close so a brief drop does not split the transmission.
                    if(mSquelchDelayTimeMs > 0 && mDelayExecutor != null)
                    {
                        if(mPendingClose != null && !mPendingClose.isDone())
                        {
                            mPendingClose.cancel(false);
                        }

                        mPendingClose = mDelayExecutor.schedule(() ->
                        {
                            //Only close if still squelched when the delay elapses.
                            if(mSquelchState == SquelchState.SQUELCH)
                            {
                                closeAudioSegment();
                            }
                        }, mSquelchDelayTimeMs, TimeUnit.MILLISECONDS);
                    }
                    else
                    {
                        closeAudioSegment();
                    }
                }
                else
                {
                    //Squelch reopened - cancel any pending close so the segment continues as one clip.
                    if(mPendingClose != null && !mPendingClose.isDone())
                    {
                        mPendingClose.cancel(false);

                        //If preserving silence, back-fill the gap so inter-transmission timing is retained.
                        if(!mSquelchDelayRemoveSilence && mSquelchClosedTimestamp > 0)
                        {
                            long elapsedMs = System.currentTimeMillis() - mSquelchClosedTimestamp;
                            //Round up to the nearest 100ms to match the silence chunk increments.
                            int silenceDuration = (int)(((elapsedMs + 99) / 100) * 100);

                            if(silenceDuration > 0 && silenceDuration <= mSquelchDelayTimeMs)
                            {
                                addSilence(silenceDuration);
                            }
                        }
                    }

                    mSquelchClosedTimestamp = 0;
                }
            }
        }
    }
}