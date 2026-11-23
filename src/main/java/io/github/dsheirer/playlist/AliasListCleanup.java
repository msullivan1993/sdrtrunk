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

package io.github.dsheirer.playlist;

import io.github.dsheirer.alias.Alias;
import io.github.dsheirer.alias.AliasModel;
import io.github.dsheirer.alias.id.AliasID;
import io.github.dsheirer.alias.id.talkgroup.StreamAsTalkgroup;
import io.github.dsheirer.alias.id.talkgroup.Talkgroup;
import io.github.dsheirer.alias.id.talkgroup.TalkgroupRange;
import io.github.dsheirer.controller.channel.Channel;
import io.github.dsheirer.module.decode.DecoderType;
import io.github.dsheirer.module.decode.analog.DecodeConfigAnalog;
import io.github.dsheirer.protocol.Protocol;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Finds the alias lists and aliases that belong only to a channel, so they can be offered for deletion along with the
 * channel, and the alias lists that no channel uses.
 *
 * Alias list names exist only as references from aliases, channels and alias list configurations, so deleting a
 * channel leaves its aliases (and so its alias list) behind.
 */
public final class AliasListCleanup
{
    private AliasListCleanup()
    {
    }

    /**
     * Channels other than the excluded channel that use the alias list.
     */
    public static List<Channel> getOtherChannels(String aliasListName, Channel excluded, Collection<Channel> channels)
    {
        List<Channel> others = new ArrayList<>();

        if(aliasListName != null && channels != null)
        {
            for(Channel channel : channels)
            {
                if(channel != excluded && aliasListName.equals(channel.getAliasListName()))
                {
                    others.add(channel);
                }
            }
        }

        return others;
    }

    /**
     * Aliases in the alias list.
     */
    public static List<Alias> getAliases(String aliasListName, Collection<Alias> aliases)
    {
        List<Alias> listAliases = new ArrayList<>();

        if(aliasListName != null && aliases != null)
        {
            for(Alias alias : aliases)
            {
                if(aliasListName.equals(alias.getAliasListName()))
                {
                    listAliases.add(alias);
                }
            }
        }

        return listAliases;
    }

    /**
     * Talkgroup protocol for an analog channel's assigned talkgroup, or null for channels whose talkgroups are
     * decoded off the air.
     */
    public static Protocol getAssignedTalkgroupProtocol(Channel channel)
    {
        if(channel != null && channel.getDecodeConfiguration() instanceof DecodeConfigAnalog analog)
        {
            DecoderType decoderType = analog.getDecoderType();

            if(decoderType == DecoderType.NBFM)
            {
                return Protocol.NBFM;
            }
            else if(decoderType == DecoderType.AM)
            {
                return Protocol.AM;
            }
        }

        return null;
    }

    /**
     * Finds the alias for an analog channel's assigned talkgroup when that alias belongs only to the channel: the
     * alias's only talkgroup identifier is the channel's talkgroup and no other channel in the alias list is an
     * analog channel with the same talkgroup.  Channels with decoded talkgroups (P25, DMR, NXDN, trunked) have no
     * reliable link to particular aliases, so none is returned for them.
     *
     * @param channel being deleted
     * @param listAliases aliases in the channel's alias list
     * @param otherChannels other channels that use the channel's alias list
     * @return alias or null
     */
    public static Alias getChannelTalkgroupAlias(Channel channel, Collection<Alias> listAliases,
                                                 Collection<Channel> otherChannels)
    {
        Protocol protocol = getAssignedTalkgroupProtocol(channel);

        if(protocol == null || listAliases == null)
        {
            return null;
        }

        int talkgroup = ((DecodeConfigAnalog)channel.getDecodeConfiguration()).getTalkgroup();

        if(otherChannels != null)
        {
            for(Channel other : otherChannels)
            {
                if(getAssignedTalkgroupProtocol(other) == protocol &&
                    ((DecodeConfigAnalog)other.getDecodeConfiguration()).getTalkgroup() == talkgroup)
                {
                    return null;
                }
            }
        }

        Alias match = null;

        for(Alias alias : listAliases)
        {
            List<AliasID> talkgroupIds = getTalkgroupIdentifiers(alias);

            boolean matches = talkgroupIds.stream().anyMatch(id -> id instanceof Talkgroup tg &&
                tg.getProtocol() == protocol && tg.getValue() == talkgroup);

            if(matches)
            {
                //Ambiguous when more than one alias matches or the alias also covers other talkgroups
                if(match != null || talkgroupIds.size() != 1)
                {
                    return null;
                }

                match = alias;
            }
        }

        return match;
    }

    /**
     * Talkgroup and talkgroup range identifiers of the alias, excluding Stream As talkgroups.
     */
    private static List<AliasID> getTalkgroupIdentifiers(Alias alias)
    {
        List<AliasID> ids = new ArrayList<>();

        for(AliasID id : alias.getAliasIdentifiers())
        {
            if(id instanceof StreamAsTalkgroup)
            {
                continue;
            }

            if(id instanceof Talkgroup || id instanceof TalkgroupRange)
            {
                ids.add(id);
            }
        }

        return ids;
    }

    /**
     * Alias list names that no channel uses, sorted alphabetically.
     * @param aliasListNames all alias list names
     * @param channels all channels
     * @return unused alias list names
     */
    public static List<String> getUnusedAliasLists(Collection<String> aliasListNames, Collection<Channel> channels)
    {
        List<String> unused = new ArrayList<>();

        if(aliasListNames != null)
        {
            for(String name : aliasListNames)
            {
                if(name == null || name.isEmpty() || name.equals(AliasModel.NO_ALIAS_LIST))
                {
                    continue;
                }

                boolean used = channels != null && channels.stream()
                    .anyMatch(channel -> name.equals(channel.getAliasListName()));

                if(!used && !unused.contains(name))
                {
                    unused.add(name);
                }
            }
        }

        unused.sort(String.CASE_INSENSITIVE_ORDER);
        return unused;
    }
}
