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
import io.github.dsheirer.identifier.Identifier;
import io.github.dsheirer.identifier.IdentifierUpdateNotification;
import io.github.dsheirer.identifier.radio.ISignallingRadioIdentifier;
import io.github.dsheirer.identifier.Role;
import io.github.dsheirer.dsp.filter.design.FilterDesignException;
import io.github.dsheirer.dsp.filter.fir.FIRFilterSpecification;
import io.github.dsheirer.dsp.filter.fir.real.IRealFilter;
import io.github.dsheirer.dsp.filter.fir.remez.RemezFIRFilterDesigner;
import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.sample.real.IRealBufferListener;
import io.github.dsheirer.util.ThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Provides packaging of demodulated audio sample buffers into audio segments for broadcast to registered listeners.
 * Includes audio packet metadata in constructed audio segments.
 *
 * Incorporates audio squelch state listener to control if audio packets are broadcast or ignored.
 *
 * Optional hang time: when configured, the audio segment is kept open for the hang time after the squelch closes, so
 * that a transmission and a reply, or a transmission that briefly drops out, are captured as a single call instead of
 * being split into multiple calls.  If the squelch reopens within the hang time, audio continues in the same segment.
 */
public class AudioModule extends AbstractAudioModule implements ISquelchStateListener, IRealBufferListener,
    Listener<float[]>
{
    private static final Logger mLog = LoggerFactory.getLogger(AudioModule.class);
    private List<AbstractAudioFilter>mAudioFilters;
    private final SquelchStateListener mSquelchStateListener = new SquelchStateListener();
    private static final int AUDIO_SAMPLE_RATE = 8000;
    private static final int SILENCE_CHUNK_MS = 100;
    private volatile SquelchState mSquelchState = SquelchState.SQUELCH;
    private final int mHangTimeMs;
    private final boolean mHangTimeRemoveSilence;
    private ScheduledFuture<?> mPendingClose;
    private long mSquelchClosedNanos = 0;
    private long mSquelchGeneration = 0;

    /**
     * Calling radio (FROM) identifiers from analog signalling (e.g. an MDC-1200 PTT ID) belong to one transmission.
     * When a new call opens, a signalling calling radio older than this is left over from an earlier call and is
     * removed, so that it isn't attached to a call from a radio that sent no ID.  Only signalling calling radio
     * identifiers are timed and removed: CTCSS/DCS tone identifiers are also FROM identifiers, and they arrive at the
     * start of every call, so they must not keep an old calling radio alive.  A pre-ID can decode shortly before the squelch
     * opens (e.g. while a CTCSS-gated squelch is still confirming the tone), so recent identifiers are kept.
     */
    private static final long STALE_FROM_IDENTIFIER_NANOS = TimeUnit.MILLISECONDS.toNanos(750);
    private long mLastCallingRadioNanos = 0;
    private Listener<IdentifierUpdateNotification> mIdentifierUpdateListener;

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
        this(aliasList, timeslot, maxAudioSegmentLength, audioFilters, 0, false);
    }

    /**
     * Creates an Audio Module with a hang time.
     *
     * @param aliasList for aliasing identifiers
     * @param timeslot for this audio module
     * @param maxAudioSegmentLength in milliseconds
     * @param audioFilters a list of audio filters to apply to the incoming audio samples
     * @param hangTimeMs time in milliseconds to keep the audio segment open after the squelch closes; 0 disables
     * @param hangTimeRemoveSilence true to join the audio either side of a gap directly, false to insert silence for the
     * duration of the gap so that the call keeps its real timing
     */
    public AudioModule(AliasList aliasList, int timeslot, long maxAudioSegmentLength, List<AbstractAudioFilter> audioFilters,
                       int hangTimeMs, boolean hangTimeRemoveSilence)
    {
        super(aliasList, timeslot, maxAudioSegmentLength);
        mAudioFilters = audioFilters;
        mHangTimeMs = Math.max(0, hangTimeMs);
        mHangTimeRemoveSilence = hangTimeRemoveSilence;
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
        mHangTimeMs = 0;
        mHangTimeRemoveSilence = false;
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
        cancelPendingClose();
        super.stop();
    }

    /**
     * Cancels a pending hang time close, if there is one.
     * @return true if a pending close was cancelled before it ran
     */
    private synchronized boolean cancelPendingClose()
    {
        mSquelchGeneration++;

        if(mPendingClose != null)
        {
            boolean cancelled = mPendingClose.cancel(false);
            mPendingClose = null;
            return cancelled;
        }

        return false;
    }

    /**
     * Schedules the audio segment to close when the hang time elapses, unless the squelch reopens first.  Uses the
     * shared scheduled executor, so no thread is created per channel.
     */
    private synchronized void schedulePendingClose()
    {
        if(mPendingClose != null)
        {
            mPendingClose.cancel(false);
        }

        long generation = ++mSquelchGeneration;

        mPendingClose = ThreadPool.SCHEDULED.schedule(() -> {
            synchronized(AudioModule.this)
            {
                //Close only if the squelch has not changed state since this close was scheduled
                if(generation != mSquelchGeneration || mSquelchState != SquelchState.SQUELCH)
                {
                    return;
                }

                mPendingClose = null;
            }

            closeAudioSegment();
        }, mHangTimeMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Adds silence to the current audio segment for the duration of a gap, in 100 ms chunks.
     * @param durationMs of silence to add
     */
    private void addSilence(int durationMs)
    {
        float[] chunk = new float[AUDIO_SAMPLE_RATE * SILENCE_CHUNK_MS / 1000];
        int remainingMs = durationMs;

        while(remainingMs >= SILENCE_CHUNK_MS)
        {
            addAudio(chunk);
            remainingMs -= SILENCE_CHUNK_MS;
        }

        if(remainingMs > 0)
        {
            addAudio(new float[AUDIO_SAMPLE_RATE * remainingMs / 1000]);
        }
    }

    /**
     * Wraps the identifier update listener to track when calling radio (FROM) identifiers arrive and to keep the
     * first calling radio of a call.
     *
     * Analog signalling (MDC-1200, Fleetsync) can identify more than one unit during a call, e.g. when a unit replies
     * before the repeater's squelch tail drops and both transmissions are in the same call.  The first calling radio
     * identified during the call stays attached to the call, whether the agency uses pre-IDs or post-IDs, unless a
     * later calling radio is an emergency, which replaces it.  Decoders remove the previous calling radio before they
     * add a new one, so calling radio removes are dropped here and the replacement is handled when the add arrives.
     */
    @Override
    public Listener<IdentifierUpdateNotification> getIdentifierUpdateListener()
    {
        if(mIdentifierUpdateListener == null)
        {
            Listener<IdentifierUpdateNotification> listener = super.getIdentifierUpdateListener();

            mIdentifierUpdateListener = notification -> {
                Identifier<?> identifier = notification.getIdentifier();

                if(identifier instanceof ISignallingRadioIdentifier && identifier.getRole() == Role.FROM)
                {
                    if(notification.isRemove() || notification.isSilentRemove())
                    {
                        return;
                    }

                    List<Identifier> current = getCallingRadioIdentifiers();

                    if(!current.isEmpty() && !current.contains(identifier))
                    {
                        if(isEmergency(identifier) && current.stream().noneMatch(AudioModule::isEmergency))
                        {
                            for(Identifier existing : current)
                            {
                                listener.receive(new IdentifierUpdateNotification(existing,
                                    IdentifierUpdateNotification.Operation.REMOVE, notification.getTimeslot()));
                            }
                        }
                        else
                        {
                            //Keep the first calling radio of the call
                            return;
                        }
                    }

                    mLastCallingRadioNanos = System.nanoTime();
                }

                listener.receive(notification);
            };
        }

        return mIdentifierUpdateListener;
    }

    /**
     * Signalling calling radio (FROM) identifiers currently attached.
     */
    private List<Identifier> getCallingRadioIdentifiers()
    {
        List<Identifier> callingRadios = new ArrayList<>();

        for(Identifier identifier : getIdentifierCollection().getIdentifiers(Role.FROM))
        {
            if(identifier instanceof ISignallingRadioIdentifier)
            {
                callingRadios.add(identifier);
            }
        }

        return callingRadios;
    }

    /**
     * Indicates if the identifier is a calling radio identified by an emergency message.
     */
    private static boolean isEmergency(Identifier<?> identifier)
    {
        return identifier instanceof ISignallingRadioIdentifier signalling && signalling.isEmergency();
    }

    /**
     * Removes calling radio (FROM) identifiers left over from an earlier call, when a new call opens.
     */
    private void removeStaleFromIdentifiers()
    {
        if(System.nanoTime() - mLastCallingRadioNanos > STALE_FROM_IDENTIFIER_NANOS)
        {
            for(Identifier callingRadio : getCallingRadioIdentifiers())
            {
                getIdentifierCollection().remove(callingRadio);
            }
        }
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
                //The filters work in place, and the demodulated audio buffer is shared with the auxiliary decoders
                //(MDC-1200, Fleetsync, DCS, etc.) that receive it after this module, so filter a copy.  Otherwise the
                //auxiliary decoders get de-emphasized, high-pass filtered and clipped audio while the squelch is open.
                audioBuffer = Arrays.copyOf(audioBuffer, audioBuffer.length);

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
     * Wrapper for squelch state listener
     */
    public class SquelchStateListener implements Listener<SquelchStateEvent>
    {
        @Override
        public void receive(SquelchStateEvent event)
        {
            SquelchState squelchState = event.getSquelchState();

            if(mSquelchState != squelchState)
            {
                mSquelchState = squelchState;

                if(mSquelchState == SquelchState.SQUELCH)
                {
                    if(mHangTimeMs > 0)
                    {
                        mSquelchClosedNanos = System.nanoTime();
                        schedulePendingClose();
                    }
                    else
                    {
                        closeAudioSegment();
                    }
                }
                else if(mHangTimeMs > 0)
                {
                    //Squelch reopened.  If it reopened within the hang time, the audio continues in the same segment.
                    if(cancelPendingClose())
                    {
                        if(!mHangTimeRemoveSilence && mSquelchClosedNanos > 0)
                        {
                            long gapMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - mSquelchClosedNanos);

                            if(gapMs > 0 && gapMs <= mHangTimeMs)
                            {
                                addSilence((int)gapMs);
                            }
                        }
                    }
                    else
                    {
                        //New call
                        removeStaleFromIdentifiers();
                    }

                    mSquelchClosedNanos = 0;
                }
                else
                {
                    //New call
                    removeStaleFromIdentifiers();
                }
            }
        }
    }
}
