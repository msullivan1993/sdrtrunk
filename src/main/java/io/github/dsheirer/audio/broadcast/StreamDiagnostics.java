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

package io.github.dsheirer.audio.broadcast;

import io.github.dsheirer.alias.Alias;
import io.github.dsheirer.alias.AliasList;
import io.github.dsheirer.alias.id.broadcast.BroadcastChannel;
import io.github.dsheirer.audio.AudioSegment;
import io.github.dsheirer.identifier.Identifier;
import io.github.dsheirer.identifier.IdentifierClass;
import io.github.dsheirer.identifier.IdentifierCollection;
import io.github.dsheirer.identifier.configuration.ChannelNameConfigurationIdentifier;
import io.github.dsheirer.identifier.configuration.FrequencyConfigurationIdentifier;
import io.github.dsheirer.identifier.configuration.SiteConfigurationIdentifier;
import io.github.dsheirer.identifier.configuration.SystemConfigurationIdentifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stream diagnostics (issues #2589, #2233): logs, to its own stream_diagnostics.log, how each audio segment gets its
 * streaming (broadcast) channels and what is uploaded, to find calls that are streamed to a stream that the call's
 * talkgroup isn't aliased for.
 *
 * An audio segment adds the broadcast channels of the aliases of every identifier that is added to it, and never
 * removes them.  A segment that starts with the previous call's talkgroup, or that is briefly given a wrong
 * talkgroup or a radio with a streaming alias, keeps those streams and is uploaded with its final talkgroup.
 *
 * Log lines:
 * <ul>
 *     <li>TO_CHANGE - the segment's TO identifier changed during the call</li>
 *     <li>SENT - a segment was uploaded, and every stream is justified by its final TO identifier's aliases</li>
 *     <li>LEAK - a segment was uploaded to a stream that its final TO identifier's aliases don't include, with the
 *     identifier that added the stream and the segment's identifier timeline</li>
 * </ul>
 * Diagnostic only: doesn't change what is streamed.
 */
public final class StreamDiagnostics
{
    public static final String LOGGER_NAME = "STREAMDIAG";
    public static final String LOG_FILENAME = "stream_diagnostics.log";
    private static final Logger LOG = LoggerFactory.getLogger(LOGGER_NAME);

    private StreamDiagnostics()
    {
    }

    /**
     * Logs a TO identifier change on an audio segment.
     * @param segment audio segment
     * @param previous TO identifier
     * @param next TO identifier
     * @param offsetMs since the segment started
     */
    public static void toChange(AudioSegment segment, Identifier previous, Identifier next, long offsetMs)
    {
        LOG.info("TO_CHANGE " + context(segment.getIdentifierCollection(), segment) + " +" + offsetMs + "ms " +
            describe(previous) + " -> " + describe(next));
    }

    /**
     * Checks the streams for an audio segment upload against the final TO identifier's aliases and logs SENT or LEAK.
     * @param segment audio segment
     * @param identifiers uploaded with the segment
     * @param broadcastChannels the segment is uploaded to
     */
    public static void upload(AudioSegment segment, IdentifierCollection identifiers,
                              Set<BroadcastChannel> broadcastChannels)
    {
        try
        {
            Identifier to = identifiers.getToIdentifier();
            Identifier from = identifiers.getFromIdentifier();
            AliasList aliasList = segment.getAliasList();
            Set<String> expected = new TreeSet<>();
            List<String> toAliases = new ArrayList<>();
            String streamAs = null;

            if(to != null && aliasList != null)
            {
                for(Alias alias : aliasList.getAliases(to))
                {
                    toAliases.add(alias.getName());

                    for(BroadcastChannel channel : alias.getBroadcastChannels())
                    {
                        expected.add(channel.getChannelName());
                    }

                    if(alias.getStreamTalkgroupAlias() != null)
                    {
                        streamAs = String.valueOf(alias.getStreamTalkgroupAlias().getValue());
                    }
                }
            }

            Set<String> streams = new TreeSet<>();
            List<BroadcastChannel> leaked = new ArrayList<>();

            for(BroadcastChannel channel : broadcastChannels)
            {
                streams.add(channel.getChannelName());

                if(!expected.contains(channel.getChannelName()))
                {
                    leaked.add(channel);
                }
            }

            String summary = context(identifiers, segment) + " TO=" + describe(to) + " toAliases=" + toAliases +
                (streamAs != null ? " streamAs=" + streamAs : "") + " FROM=" + describe(from) + " streams=" +
                streams + " duration=" + segment.getDuration() + "ms";

            if(leaked.isEmpty())
            {
                LOG.info("SENT " + summary);
            }
            else
            {
                StringBuilder sb = new StringBuilder();
                sb.append("LEAK ").append(summary);

                for(BroadcastChannel channel : leaked)
                {
                    sb.append("\n    stream [").append(channel.getChannelName()).append("] not aliased for final TO - added by: ")
                        .append(segment.getBroadcastChannelSource(channel));
                }

                sb.append("\n    timeline:");

                for(String entry : segment.getDiagnosticTimeline())
                {
                    sb.append("\n      ").append(entry);
                }

                LOG.info(sb.toString());
            }
        }
        catch(Exception e)
        {
            LOG.error("Error logging stream diagnostics", e);
        }
    }

    /**
     * Logs a FILTERED line when the completed-call stream filter (upstream PR #2789) removes streams that the call
     * collected but that no identifier still in the call routes to.  When nothing is kept, the call isn't streamed.
     * @param segment completed audio segment
     * @param identifiers final identifiers of the call
     * @param accumulated streams the segment collected while the call was open
     * @param kept streams the call is sent to after filtering
     */
    public static void filtered(AudioSegment segment, IdentifierCollection identifiers,
                                Set<BroadcastChannel> accumulated, Set<BroadcastChannel> kept)
    {
        try
        {
            if(accumulated == null || accumulated.isEmpty())
            {
                return;
            }

            List<BroadcastChannel> dropped = new ArrayList<>();

            for(BroadcastChannel channel : accumulated)
            {
                if(kept == null || !kept.contains(channel))
                {
                    dropped.add(channel);
                }
            }

            if(dropped.isEmpty())
            {
                return;
            }

            Set<String> keptNames = new TreeSet<>();

            if(kept != null)
            {
                for(BroadcastChannel channel : kept)
                {
                    keptNames.add(channel.getChannelName());
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("FILTERED ").append(context(identifiers, segment))
                .append(" TO=").append(describe(identifiers.getToIdentifier()))
                .append(" FROM=").append(describe(identifiers.getFromIdentifier()))
                .append(" kept=").append(keptNames)
                .append(keptNames.isEmpty() ? " - CALL NOT STREAMED" : "")
                .append(" duration=").append(segment.getDuration()).append("ms");

            for(BroadcastChannel channel : dropped)
            {
                sb.append("\n    dropped stream [").append(channel.getChannelName()).append("] - added by: ")
                    .append(segment.getBroadcastChannelSource(channel));
            }

            sb.append("\n    timeline:");

            for(String entry : segment.getDiagnosticTimeline())
            {
                sb.append("\n      ").append(entry);
            }

            LOG.info(sb.toString());
        }
        catch(Exception e)
        {
            LOG.error("Error logging stream diagnostics", e);
        }
    }

    /**
     * Describes an identifier as role, form, value and class.
     */
    public static String describe(Identifier identifier)
    {
        if(identifier == null)
        {
            return "none";
        }

        return identifier.getRole() + " " + identifier.getForm() + " " + identifier + " (" +
            identifier.getClass().getSimpleName() + ")";
    }

    /**
     * System, site, channel name, frequency and timeslot of the segment.
     */
    private static String context(IdentifierCollection identifiers, AudioSegment segment)
    {
        String system = null;
        String site = null;
        String channel = null;
        Long frequency = null;

        for(Identifier identifier : identifiers.getIdentifiers(IdentifierClass.CONFIGURATION))
        {
            if(identifier instanceof SystemConfigurationIdentifier)
            {
                system = String.valueOf(identifier.getValue());
            }
            else if(identifier instanceof SiteConfigurationIdentifier)
            {
                site = String.valueOf(identifier.getValue());
            }
            else if(identifier instanceof ChannelNameConfigurationIdentifier)
            {
                channel = String.valueOf(identifier.getValue());
            }
            else if(identifier instanceof FrequencyConfigurationIdentifier frequencyIdentifier)
            {
                frequency = frequencyIdentifier.getValue();
            }
        }

        return "[" + system + "/" + site + "/" + channel + (frequency != null ? " @" + frequency : "") +
            (segment.getTimeslot() > 0 ? " TS" + segment.getTimeslot() : "") + "]";
    }
}
