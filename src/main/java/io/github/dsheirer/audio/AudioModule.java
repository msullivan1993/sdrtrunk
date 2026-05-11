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
import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.sample.real.IRealBufferListener;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private List<AbstractAudioFilter> mAudioFilters;
    private final int mSquelchDelayTimeMs;
    private final boolean mSquelchDelayRemoveSilence;
    private ScheduledExecutorService mDelayExecutor;
    private ScheduledFuture<?> mPendingClose;
    private final SquelchStateListener mSquelchStateListener = new SquelchStateListener();
    private SquelchState mSquelchState = SquelchState.SQUELCH;

    /**
     * Creates an Audio Module.
     *
     * @param aliasList for aliasing identifiers
     * @param timeslot for this audio module
     * @param maxAudioSegmentLength in milliseconds
     * @param audioFilters a list of audio filters to apply to the incoming audio samples
     * @param squelchDelayTimeMs delay time before closing audio segment after squelch closes
     * @param squelchDelayRemoveSilence true to remove silence during delay, false to preserve it
     */
    public AudioModule(AliasList aliasList, int timeslot, long maxAudioSegmentLength,
                       List<AbstractAudioFilter> audioFilters, int squelchDelayTimeMs,
                       boolean squelchDelayRemoveSilence)
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
    public AudioModule(AliasList aliasList, List<AbstractAudioFilter> audioFilters)
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
        // Cancel any pending close
        if(mPendingClose != null && !mPendingClose.isDone())
        {
            mPendingClose.cancel(false);
        }

        // Shutdown the executor
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

    /**
     * Adds silence to the audio segment for the specified duration in milliseconds.
     * @param durationMs duration of silence in milliseconds
     */
    private void addSilence(int durationMs)
    {
        // Add silence in 100ms chunks at 8kHz sample rate (800 samples per chunk)
        int chunkSize = 800; // 100ms at 8kHz
        float[] silenceChunk = new float[chunkSize];

        int remainingMs = durationMs;
        while(remainingMs >= 100)
        {
            addAudio(silenceChunk);
            remainingMs -= 100;
        }

        // Add any remaining partial chunk
        if(remainingMs > 0)
        {
            int remainingSamples = (AUDIO_SAMPLE_RATE * remainingMs) / 1000;
            float[] partialChunk = new float[remainingSamples];
            addAudio(partialChunk);
        }
    }

    @Override
    public Listener<float[]> getBufferListener()
    {
        //Redirect received reusable buffers to the receive(buffer) method
        return this;
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
                    // Record when squelch closed
                    mSquelchClosedTimestamp = System.currentTimeMillis();

                    // If delay is configured, schedule the close; otherwise close immediately
                    if(mSquelchDelayTimeMs > 0 && mDelayExecutor != null)
                    {
                        // Cancel any existing pending close
                        if(mPendingClose != null && !mPendingClose.isDone())
                        {
                            mPendingClose.cancel(false);
                        }

                        // Schedule delayed close
                        mPendingClose = mDelayExecutor.schedule(() -> {
                            // Only close if still squelched
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
                    // Squelch opened - cancel any pending close
                    if(mPendingClose != null && !mPendingClose.isDone())
                    {
                        mPendingClose.cancel(false);

                        // If not removing silence, add silence for the time squelch was closed
                        if(!mSquelchDelayRemoveSilence && mSquelchClosedTimestamp > 0)
                        {
                            long elapsedMs = System.currentTimeMillis() - mSquelchClosedTimestamp;
                            // Round up to nearest 100ms to match delay increments
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