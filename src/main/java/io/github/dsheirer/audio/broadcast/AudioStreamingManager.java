/*
 * *****************************************************************************
 * Copyright (C) 2014-2024 Dennis Sheirer
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

package io.github.dsheirer.audio.broadcast;

import io.github.dsheirer.alias.Alias;
import io.github.dsheirer.alias.AliasList;
import io.github.dsheirer.alias.id.broadcast.BroadcastChannel;
import io.github.dsheirer.audio.AudioSegment;
import io.github.dsheirer.identifier.Identifier;
import io.github.dsheirer.identifier.IdentifierCollection;
import io.github.dsheirer.identifier.MutableIdentifierCollection;
import io.github.dsheirer.identifier.Role;
import io.github.dsheirer.identifier.patch.PatchGroup;
import io.github.dsheirer.identifier.patch.PatchGroupIdentifier;
import io.github.dsheirer.settings.Settings;
import io.github.dsheirer.record.AudioSegmentRecorder;
import io.github.dsheirer.record.RecordFormat;
import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.util.ThreadPool;
import io.github.dsheirer.util.TimeStamp;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Audio streaming manager monitors audio segments through completion and creates temporary streaming recordings on
 * disk and enqueues the temporary recording for streaming.
 */
public class AudioStreamingManager implements Listener<AudioSegment>
{
    private final static Logger mLog = LoggerFactory.getLogger(AudioStreamingManager.class);
    private LinkedTransferQueue<AudioSegment> mNewAudioSegments = new LinkedTransferQueue<>();
    private List<AudioSegment> mAudioSegments = new ArrayList<>();
    private Listener<AudioRecording> mAudioRecordingListener;
    private BroadcastFormat mBroadcastFormat;
    private Settings mSettings;
    private ScheduledFuture<?> mAudioSegmentProcessorFuture;
    private int mNextRecordingNumber = 1;

    /**
     * Constructs an instance
     * @param listener to receive completed audio recordings
     * @param broadcastFormat for temporary recordings
     * @param settings to manage recording directories
     */
    public AudioStreamingManager(Listener<AudioRecording> listener, BroadcastFormat broadcastFormat, Settings settings)
    {
        mAudioRecordingListener = listener;
        mBroadcastFormat = broadcastFormat;
        mSettings = settings;
    }

    /**
     * Primary receive method
     */
    @Override
    public void receive(AudioSegment audioSegment)
    {
        mNewAudioSegments.add(audioSegment);
    }

    /**
     * Starts the scheduled audio segment processor
     */
    public void start()
    {
        if(mAudioSegmentProcessorFuture == null)
        {
            mAudioSegmentProcessorFuture = ThreadPool.SCHEDULED.scheduleAtFixedRate(new AudioSegmentProcessor(),
                0, 250, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Stops the scheduled audio segment processor
     */
    public void stop()
    {
        if(mAudioSegmentProcessorFuture != null)
        {
            mAudioSegmentProcessorFuture.cancel(true);
            mAudioSegmentProcessorFuture = null;
        }

        for(AudioSegment audioSegment: mNewAudioSegments)
        {
            audioSegment.decrementConsumerCount();
        }

        mNewAudioSegments.clear();

        for(AudioSegment audioSegment: mAudioSegments)
        {
            audioSegment.decrementConsumerCount();
        }

        mAudioSegments.clear();
    }

    /**
     * Main processing method to process audio segments
     */
    private void processAudioSegments()
    {
        mNewAudioSegments.drainTo(mAudioSegments);

        Iterator<AudioSegment> it = mAudioSegments.iterator();
        AudioSegment audioSegment;
        while(it.hasNext())
        {
            audioSegment = it.next();

            if(audioSegment.isDuplicate() && mSettings.getCallManagementSettings().isDuplicateStreamingSuppressionEnabled())
            {
                it.remove();
                audioSegment.decrementConsumerCount();
            }
            else if(audioSegment.completeProperty().get())
            {
                it.remove();

                if(mAudioRecordingListener != null && audioSegment.hasBroadcastChannels())
                {
                    IdentifierCollection identifiers =
                            new IdentifierCollection(audioSegment.getIdentifierCollection().getIdentifiers());

                    if(identifiers.getToIdentifier() instanceof PatchGroupIdentifier patchGroupIdentifier)
                    {
                        if(mSettings.getCallManagementSettings()
                                .getPatchGroupStreamingOption() == PatchGroupStreamingOption.TALKGROUPS)
                        {
                            //Decompose the patch group into the individual (patched) talkgroups and process the audio
                            //segment for each patched talkgroup.
                            PatchGroup patchGroup = patchGroupIdentifier.getValue();

                            List<Identifier> ids = new ArrayList<>();
                            ids.addAll(patchGroup.getPatchedTalkgroupIdentifiers());
                            ids.addAll(patchGroup.getPatchedRadioIdentifiers());

                            //If there are no patched radios/talkgroups, override user preference and stream as a patch group
                            if(ids.isEmpty() || audioSegment.getAliasList() == null)
                            {
                                processAudioSegment(audioSegment, identifiers, audioSegment.getBroadcastChannels());
                            }
                            else
                            {
                                AliasList aliasList = audioSegment.getAliasList();

                                for(Identifier identifier: ids)
                                {
                                    List<Alias> aliases = aliasList.getAliases(identifier);
                                    Set<BroadcastChannel> broadcastChannels = new HashSet<>();
                                    for(Alias alias: aliases)
                                    {
                                        broadcastChannels.addAll(alias.getBroadcastChannels());
                                    }

                                    if(!broadcastChannels.isEmpty())
                                    {
                                        MutableIdentifierCollection decomposedIdentifiers =
                                                new MutableIdentifierCollection(identifiers.getIdentifiers());
                                        //Remove patch group TO identifier & replace with the patched talkgroup/radio
                                        decomposedIdentifiers.remove(Role.TO);
                                        decomposedIdentifiers.update(identifier);
                                        processAudioSegment(audioSegment, decomposedIdentifiers, broadcastChannels);
                                    }
                                }
                            }
                        }
                        else
                        {
                            processAudioSegment(audioSegment, identifiers, audioSegment.getBroadcastChannels());
                        }
                    }
                    else
                    {
                        //Filter the accumulated broadcast channel set down to the stream(s) an identifier still
                        //resident in this completed call actually routes to, so a stale channel left by a previous
                        //talkgroup is not streamed under the current talkgroup.  See
                        //broadcastChannelsForCurrentIdentifiers().
                        processAudioSegment(audioSegment, identifiers, broadcastChannelsForCurrentIdentifiers(
                                audioSegment.getBroadcastChannels(), audioSegment.getAliasList(), identifiers));
                    }
                }

                audioSegment.decrementConsumerCount();
            }
        }
    }

    /**
     * Restricts a completed non-patch call's accumulated broadcast channel set to only the channels that an alias of
     * an identifier still resident in the call routes to - the final TO, the source radio (FROM), or any other
     * current identifier.
     *
     * <p>An audio segment accumulates a broadcast channel for every identifier it carries while the call is open.
     * When a call terminator is missed, or a segment is seeded from a stale identifier collection, a previous
     * talkgroup's TO can leave its broadcast channel in the accumulated set.  Streaming to that raw set then uploads
     * the call to a stream that belongs to the PREVIOUS talkgroup, tagged with the CURRENT TO.  Recomputing the set
     * from the identifiers the call currently carries keeps it only on the stream(s) those identifiers route to.
     *
     * <p>Both TO- and FROM-routed streams are preserved: an operator can attach a broadcast channel to a radio
     * (source) alias - "stream everything unit N transmits" - and that stream is legitimate even when the TO
     * talkgroup routes elsewhere.  Patch groups are decomposed before this method and never reach it.
     *
     * <p>Never drops the call to zero channels: when none of the accumulated channels are routed by a current
     * identifier but those identifiers do route somewhere, the current identifiers' alias channels are used
     * directly, so a legitimate call reaches its live streams rather than nowhere.
     *
     * @param accumulated broadcast channels the completed segment gathered across its lifetime
     * @param aliasList for the segment, used to resolve each identifier's routed broadcast channels
     * @param identifiers currently resident in the completed call (TO, FROM, ...)
     * @return the subset of accumulated channels routed by a current identifier (or, when none survive, the current
     * identifiers' alias channels); the unfiltered input when there is nothing to filter against
     */
    static Set<BroadcastChannel> broadcastChannelsForCurrentIdentifiers(Set<BroadcastChannel> accumulated,
            AliasList aliasList, IdentifierCollection identifiers)
    {
        //Nothing to filter, or no alias list / identifiers to filter against: preserve prior behaviour.
        if(accumulated == null || accumulated.isEmpty() || aliasList == null || identifiers == null)
        {
            return accumulated;
        }

        //Broadcast channels an alias of any identifier still resident in the call routes to (TO, FROM, ...).
        Set<BroadcastChannel> routed = new HashSet<>();

        for(Identifier identifier: identifiers.getIdentifiers())
        {
            for(Alias alias: aliasList.getAliases(identifier))
            {
                routed.addAll(alias.getBroadcastChannels());
            }
        }

        //Keep only the accumulated channels a current identifier routes to; collect the rest as stale drops.
        Set<BroadcastChannel> keptChannels = new LinkedHashSet<>();
        Set<BroadcastChannel> droppedChannels = new LinkedHashSet<>();

        for(BroadcastChannel broadcastChannel: accumulated)
        {
            if(routed.contains(broadcastChannel))
            {
                keptChannels.add(broadcastChannel);
            }
            else
            {
                droppedChannels.add(broadcastChannel);
            }
        }

        //Never drop the call entirely: if none of the accumulated channels are routed by a current identifier but
        //those identifiers do route somewhere, stream to those live routes rather than nowhere.
        if(keptChannels.isEmpty() && !routed.isEmpty())
        {
            keptChannels.addAll(routed);
        }

        if(!droppedChannels.isEmpty() && mLog.isDebugEnabled())
        {
            mLog.debug("Dropped stale broadcast channel(s) " + droppedChannels + " from a completed call with final " +
                    "TO [" + identifiers.getToIdentifier() + "] - routed only by an identifier no longer resident in " +
                    "the call");
        }

        return keptChannels;
    }

    /**
     * Processes an audio segment for streaming by creating a temporary MP3 recording and submitting the recording
     * to the specific broadcast channel(s).
     * @param audioSegment to process for streaming
     * @param identifierCollection to use for the streamed audio recording.
     * @param broadcastChannels to receive the audio recording
     */
    private void processAudioSegment(AudioSegment audioSegment, IdentifierCollection identifierCollection,
                                     Set<BroadcastChannel> broadcastChannels)
    {
        Path path = getTemporaryRecordingPath();
        long length = 0;

        for(float[] audioBuffer: audioSegment.getAudioBuffers())
        {
            length += audioBuffer.length;
        }

        length /= 8; //Sample rate is 8000 samples per second, or 8 samples per millisecond.

        try
        {
            AudioSegmentRecorder.record(audioSegment, path, RecordFormat.MP3, mSettings, identifierCollection);

            AudioRecording audioRecording = new AudioRecording(path, broadcastChannels, identifierCollection,
                    audioSegment.getStartTimestamp(), length);
            mAudioRecordingListener.receive(audioRecording);
        }
        catch(IOException ioe)
        {
            mLog.error("Error recording temporary stream MP3");
        }
    }

    /**
     * Creates a temporary streaming recording file path
     */
    private Path getTemporaryRecordingPath()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(BroadcastModel.TEMPORARY_STREAM_FILE_SUFFIX);

        //Check for integer overflow and readjust negative value to 0
        if(mNextRecordingNumber < 0)
        {
            mNextRecordingNumber = 1;
        }

        int recordingNumber = mNextRecordingNumber++;

        sb.append(recordingNumber).append("_");
        sb.append(TimeStamp.getLongTimeStamp("_"));
        sb.append(mBroadcastFormat.getFileExtension());

        Path temporaryRecordingPath = mSettings.getDirectorySettings().getDirectoryStreaming().resolve(sb.toString());

        return temporaryRecordingPath;
    }

    /**
     * Scheduled runnable to process audio segments.
     */
    public class AudioSegmentProcessor implements Runnable
    {
        @Override
        public void run()
        {
            try
            {
                processAudioSegments();
            }
            catch(Throwable t)
            {
                mLog.error("Error processing audio segments for streaming", t);
            }
        }
    }
}
