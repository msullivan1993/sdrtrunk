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
import io.github.dsheirer.alias.id.talkgroup.TalkgroupFormat;
import io.github.dsheirer.alias.id.talkgroup.TalkgroupRange;
import io.github.dsheirer.protocol.Protocol;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * NXDN conventional systems almost universally use talkgroup 0
     */
    public static final int NXDN_CONVENTIONAL_DEFAULT_TALKGROUP = 0;

    /**
     * Maximum alias list name length, matching the alias editor's new/rename dialogs
     */
    public static final int MAX_ALIAS_LIST_NAME_LENGTH = 25;

    private static final Pattern TRAILING_NUMBER = Pattern.compile("^(.*?)(\\d+)$");

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
            case NXDN48, NXDN96 -> Protocol.NXDN;
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
     * Indicates if the talkgroup is decoded off the air (digital conventional) rather than assigned by the channel.
     * These channels nearly always use a fixed default talkgroup, so each channel needs its own alias list and a
     * Stream As talkgroup to keep channels apart when streaming.
     */
    public static boolean isDecodedTalkgroup(Protocol protocol)
    {
        return protocol == Protocol.APCO25 || protocol == Protocol.NXDN;
    }

    /**
     * Default conventional talkgroup for digital protocols: 1 for P25 and 0 for NXDN.
     * @return default talkgroup or null for protocols without a decoded default
     */
    public static Integer getConventionalDefaultTalkgroup(Protocol protocol)
    {
        if(protocol == Protocol.APCO25)
        {
            return P25_CONVENTIONAL_DEFAULT_TALKGROUP;
        }
        else if(protocol == Protocol.NXDN)
        {
            return NXDN_CONVENTIONAL_DEFAULT_TALKGROUP;
        }

        return null;
    }

    /**
     * Suggested talkgroup for a new channel: the next free value in the alias list for analog channels, or the
     * conventional default (P25 1, NXDN 0) for digital channels.
     */
    public static int getSuggestedTalkgroup(Protocol protocol, Collection<Alias> aliases)
    {
        Integer conventionalDefault = getConventionalDefaultTalkgroup(protocol);

        if(conventionalDefault != null)
        {
            return conventionalDefault;
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
     * Next numbered alias list name in a series, for P25 conventional channels that each need their own alias list
     * because they all use talkgroup 1.  MasonKYSO -> MasonKYSO2, and with MasonKYSO2 and MasonKYSO3 already in use,
     * either MasonKYSO or MasonKYSO3 -> MasonKYSO4.  A trailing number is treated as a series number only when the
     * base name (without the number) is also an existing alias list, so Station911 -> Station9112 unless Station
     * exists.
     * @param current alias list name
     * @param existing alias list names
     * @return next unused alias list name in the series
     */
    public static String getNextAliasListName(String current, Collection<String> existing)
    {
        if(current == null || current.isBlank())
        {
            return null;
        }

        String base = current.trim();
        Matcher matcher = TRAILING_NUMBER.matcher(base);

        if(matcher.matches() && !matcher.group(1).isEmpty() && containsIgnoreCase(existing, matcher.group(1)))
        {
            base = matcher.group(1);
        }

        int max = 1;

        if(existing != null)
        {
            for(String name : existing)
            {
                if(name != null && name.length() > base.length() && name.regionMatches(true, 0, base, 0, base.length()))
                {
                    String suffix = name.substring(base.length());

                    if(suffix.matches("\\d{1,6}"))
                    {
                        max = Math.max(max, Integer.parseInt(suffix));
                    }
                }
            }
        }

        int next = max + 1;
        String candidate;

        do
        {
            String suffix = String.valueOf(next);
            String trimmedBase = base.length() + suffix.length() > MAX_ALIAS_LIST_NAME_LENGTH ?
                base.substring(0, Math.max(1, MAX_ALIAS_LIST_NAME_LENGTH - suffix.length())) : base;
            candidate = trimmedBase + suffix;
            next++;
        }
        while(containsIgnoreCase(existing, candidate));

        return candidate;
    }

    /**
     * Alias list name for a channel that gets its own alias list: the desired name (trimmed and limited to 25
     * characters), or the next numbered name when that list already exists (Mason Fire -> Mason Fire2).
     * @return unique alias list name or null if the desired name is blank
     */
    public static String getUniqueAliasListName(String desired, Collection<String> existing)
    {
        if(desired == null || desired.isBlank())
        {
            return null;
        }

        String name = desired.trim();

        if(name.length() > MAX_ALIAS_LIST_NAME_LENGTH)
        {
            name = name.substring(0, MAX_ALIAS_LIST_NAME_LENGTH).trim();
        }

        if(!containsIgnoreCase(existing, name))
        {
            return name;
        }

        return getNextAliasListName(name, existing);
    }

    private static boolean containsIgnoreCase(Collection<String> names, String value)
    {
        if(names != null)
        {
            for(String name : names)
            {
                if(name != null && name.equalsIgnoreCase(value))
                {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Parses a talkgroup value from user text using the protocol's valid talkgroup range (NXDN and P25 allow 0).
     * @return value or null if the text is empty or invalid
     */
    public static Integer parseTalkgroup(String text, Protocol protocol)
    {
        if(protocol == null)
        {
            return parseTalkgroup(text);
        }

        if(text == null || text.isBlank())
        {
            return null;
        }

        try
        {
            int value = Integer.parseInt(text.trim());
            TalkgroupFormat format = TalkgroupFormat.get(protocol);

            if(format.getMinimumValidValue() <= value && value <= Math.min(format.getMaximumValidValue(), MAX_TALKGROUP))
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
     * Valid talkgroup range description for the protocol, for error messages
     */
    public static String getTalkgroupRange(Protocol protocol)
    {
        if(protocol == null)
        {
            return "1 to 65,535";
        }

        TalkgroupFormat format = TalkgroupFormat.get(protocol);
        return String.format("%,d to %,d", format.getMinimumValidValue(),
            Math.min(format.getMaximumValidValue(), MAX_TALKGROUP));
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
