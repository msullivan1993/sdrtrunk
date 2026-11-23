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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.dsheirer.alias.Alias;
import io.github.dsheirer.alias.id.talkgroup.StreamAsTalkgroup;
import io.github.dsheirer.alias.id.talkgroup.Talkgroup;
import io.github.dsheirer.alias.id.talkgroup.TalkgroupRange;
import io.github.dsheirer.protocol.Protocol;
import java.util.List;
import org.junit.jupiter.api.Test;

public class RadioReferenceAliasSupportTest
{
    private static Alias alias(String name, io.github.dsheirer.alias.id.AliasID... ids)
    {
        Alias alias = new Alias(name);
        alias.setAliasListName("Test");
        for(io.github.dsheirer.alias.id.AliasID id : ids)
        {
            alias.addAliasID(id);
        }
        return alias;
    }

    @Test
    public void aliasProtocolByMode()
    {
        assertEquals(Protocol.NBFM, RadioReferenceAliasSupport.getAliasProtocol(ModeDecoderType.FM));
        assertEquals(Protocol.NBFM, RadioReferenceAliasSupport.getAliasProtocol(ModeDecoderType.FMN));
        assertEquals(Protocol.AM, RadioReferenceAliasSupport.getAliasProtocol(ModeDecoderType.AM));
        assertEquals(Protocol.APCO25, RadioReferenceAliasSupport.getAliasProtocol(ModeDecoderType.APCO25));
        assertNull(RadioReferenceAliasSupport.getAliasProtocol(ModeDecoderType.DMR));
        assertNull(RadioReferenceAliasSupport.getAliasProtocol(ModeDecoderType.NXDN48));
        assertNull(RadioReferenceAliasSupport.getAliasProtocol(null));
    }

    @Test
    public void suggestedTalkgroup()
    {
        List<Alias> aliases = List.of(
            alias("A", new Talkgroup(Protocol.NBFM, 3)),
            alias("B", new TalkgroupRange(Protocol.NBFM, 10, 12)),
            alias("C", new Talkgroup(Protocol.APCO25, 500)),
            alias("D", new Talkgroup(Protocol.NBFM, 4), new StreamAsTalkgroup(9000)));

        assertEquals(13, RadioReferenceAliasSupport.getSuggestedTalkgroup(Protocol.NBFM, aliases));
        assertEquals(1, RadioReferenceAliasSupport.getSuggestedTalkgroup(Protocol.AM, aliases));
        assertEquals(1, RadioReferenceAliasSupport.getSuggestedTalkgroup(Protocol.APCO25, aliases));
        assertEquals(1, RadioReferenceAliasSupport.getSuggestedTalkgroup(Protocol.NBFM, List.of()));
    }

    @Test
    public void findExisting()
    {
        Alias a = alias("A", new Talkgroup(Protocol.APCO25, 1));
        Alias b = alias("B", new TalkgroupRange(Protocol.NBFM, 10, 12));
        Alias c = alias("C", new StreamAsTalkgroup(1));
        List<Alias> aliases = List.of(c, a, b);

        assertSame(a, RadioReferenceAliasSupport.findExisting(Protocol.APCO25, 1, aliases));
        assertSame(b, RadioReferenceAliasSupport.findExisting(Protocol.NBFM, 11, aliases));
        assertNull(RadioReferenceAliasSupport.findExisting(Protocol.NBFM, 1, aliases));
        assertNull(RadioReferenceAliasSupport.findExisting(Protocol.APCO25, 2, aliases));
        assertSame(c, RadioReferenceAliasSupport.findStreamAsTalkgroup(1, aliases));
    }

    @Test
    public void parseTalkgroup()
    {
        assertEquals(1, RadioReferenceAliasSupport.parseTalkgroup(" 1 "));
        assertEquals(65535, RadioReferenceAliasSupport.parseTalkgroup("65535"));
        assertNull(RadioReferenceAliasSupport.parseTalkgroup("0"));
        assertNull(RadioReferenceAliasSupport.parseTalkgroup("65536"));
        assertNull(RadioReferenceAliasSupport.parseTalkgroup("abc"));
        assertNull(RadioReferenceAliasSupport.parseTalkgroup(""));
        assertNull(RadioReferenceAliasSupport.parseTalkgroup(null));
    }
}
