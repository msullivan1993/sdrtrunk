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

package io.github.dsheirer.gui.playlist.radioreference;

import io.github.dsheirer.alias.Alias;
import io.github.dsheirer.alias.id.AliasID;
import io.github.dsheirer.alias.id.broadcast.BroadcastChannel;
import io.github.dsheirer.alias.id.talkgroup.StreamAsTalkgroup;
import io.github.dsheirer.alias.id.talkgroup.Talkgroup;
import io.github.dsheirer.alias.id.talkgroup.TalkgroupRange;
import io.github.dsheirer.protocol.Protocol;
import java.util.Collection;

/**
 * Alias support for conventional channels created from Radio Reference frequency entries.  Determines which talkgroup
 * identifier (if any) an imported channel produces, suggests a talkgroup value, detects aliases that already cover
 * that identifier, and builds the alias.
 */
public final class RadioReferenceAliasSupport
{
    public static final int MIN_TALKGROUP = 1;
    public static final int MAX_TALKGROUP = 65535;

    /**
     * P25 conventional repeaters almost universally use the default talkgroup 1
     */
    public static final int P25_CONVENTIONAL_DEFAULT_TALKGROUP = 1;

    private RadioReferenceAliasSupport()
    {
    }

    /**
     * Protocol of the talkgroup identifier that a conventional channel of this type produces and that can be aliased
     * at import time, or null when the channel's identifiers are better aliased by hand (DMR, NXDN).
     */
    public static Protocol getAliasProtocol(ModeDecoderType modeDecoderType)
    {
        if(modeDecoderType == null)
        {
            return null;
        }

        return switch(modeDecoderType)
        {
            case FM, FMN -> Protocol.NBFM;
            case AM -> Protocol.AM;
            case APCO25 -> Protocol.APCO25;
            default -> null;
        };
    }

    /**
     * Indicates if the talkgroup is assigned by the channel configuration (analog) rather than decoded off the air.
     */
    public static boolean isChannelAssignedTalkgroup(Protocol protocol)
    {
        return protocol == Protocol.NBFM || protocol == Protocol.AM;
    }

    /**
     * Suggested talkgroup for a new channel: the next free value in the alias list for analog channels, or the P25
     * conventional default of 1.
     */
    public static int getSuggestedTalkgroup(Protocol protocol, Collection<Alias> aliases)
    {
        if(protocol == Protocol.APCO25)
        {
            return P25_CONVENTIONAL_DEFAULT_TALKGROUP;
        }

        return getNextFreeTalkgroup(protocol, aliases);
    }

    /**
     * Next talkgroup value above the highest value aliased for the protocol in the supplied aliases, or 1 when none.
     */
    public static int getNextFreeTalkgroup(Protocol protocol, Collection<Alias> aliases)
    {
        int max = 0;

        if(aliases != null)
        {
            for(Alias alias : aliases)
            {
                for(AliasID aliasID : alias.getAliasIdentifiers())
                {
                    if(aliasID instanceof StreamAsTalkgroup)
                    {
                        continue;
                    }

                    if(aliasID instanceof Talkgroup talkgroup && talkgroup.getProtocol() == protocol)
                    {
                        max = Math.max(max, talkgroup.getValue());
                    }
                    else if(aliasID instanceof TalkgroupRange range && range.getProtocol() == protocol)
                    {
                        max = Math.max(max, range.getMaxTalkgroup());
                    }
                }
            }
        }

        if(max >= MAX_TALKGROUP)
        {
            return MAX_TALKGROUP;
        }

        return max + 1;
    }

    /**
     * Finds the first alias that already covers the protocol and talkgroup value, either as a talkgroup or within a
     * talkgroup range.
     * @return matching alias or null
     */
    public static Alias findExisting(Protocol protocol, int value, Collection<Alias> aliases)
    {
        if(protocol == null || aliases == null)
        {
            return null;
        }

        for(Alias alias : aliases)
        {
            for(AliasID aliasID : alias.getAliasIdentifiers())
            {
                if(aliasID instanceof StreamAsTalkgroup)
                {
                    continue;
                }

                if(aliasID instanceof Talkgroup talkgroup && talkgroup.getProtocol() == protocol &&
                    talkgroup.getValue() == value)
                {
                    return alias;
                }
                else if(aliasID instanceof TalkgroupRange range && range.getProtocol() == protocol &&
                    range.getMinTalkgroup() <= value && value <= range.getMaxTalkgroup())
                {
                    return alias;
                }
            }
        }

        return null;
    }

    /**
     * Finds the first alias that already streams as the specified talkgroup value.
     * @return matching alias or null
     */
    public static Alias findStreamAsTalkgroup(int value, Collection<Alias> aliases)
    {
        if(aliases == null)
        {
            return null;
        }

        for(Alias alias : aliases)
        {
            for(AliasID aliasID : alias.getAliasIdentifiers())
            {
                if(aliasID instanceof StreamAsTalkgroup streamAs && streamAs.getValue() == value)
                {
                    return alias;
                }
            }
        }

        return null;
    }

    /**
     * Parses a talkgroup value from user text.
     * @return value in the range 1 - 65,535 or null if the text is empty or invalid
     */
    public static Integer parseTalkgroup(String text)
    {
        if(text == null || text.isBlank())
        {
            return null;
        }

        try
        {
            int value = Integer.parseInt(text.trim());

            if(MIN_TALKGROUP <= value && value <= MAX_TALKGROUP)
            {
                return value;
            }
        }
        catch(NumberFormatException nfe)
        {
            //Invalid value
        }

        return null;
    }

    /**
     * Creates an alias for the imported channel.
     * @param name of the alias
     * @param aliasListName to assign
     * @param group optional alias group
     * @param protocol of the talkgroup
     * @param talkgroup value
     * @param streamAsTalkgroup optional stream-as talkgroup value
     * @param broadcastChannel optional broadcast (streaming) configuration name
     * @return alias
     */
    public static Alias createAlias(String name, String aliasListName, String group, Protocol protocol, int talkgroup,
                                    Integer streamAsTalkgroup, String broadcastChannel)
    {
        Alias alias = new Alias(name);
        alias.setAliasListName(aliasListName);

        if(group != null && !group.isBlank())
        {
            alias.setGroup(group.trim());
        }

        alias.addAliasID(new Talkgroup(protocol, talkgroup));

        if(streamAsTalkgroup != null)
        {
            alias.addAliasID(new StreamAsTalkgroup(streamAsTalkgroup));
        }

        if(broadcastChannel != null && !broadcastChannel.isBlank())
        {
            alias.addAliasID(new BroadcastChannel(broadcastChannel));
        }

        return alias;
    }
}
