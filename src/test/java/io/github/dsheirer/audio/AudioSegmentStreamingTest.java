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

package io.github.dsheirer.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dsheirer.alias.Alias;
import io.github.dsheirer.alias.AliasList;
import io.github.dsheirer.alias.id.broadcast.BroadcastChannel;
import io.github.dsheirer.alias.id.radio.Radio;
import io.github.dsheirer.alias.id.talkgroup.Talkgroup;
import io.github.dsheirer.identifier.Identifier;
import io.github.dsheirer.module.decode.p25.identifier.radio.APCO25RadioIdentifier;
import io.github.dsheirer.module.decode.p25.identifier.talkgroup.APCO25Talkgroup;
import io.github.dsheirer.protocol.Protocol;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Demonstrates how an audio segment's streaming (broadcast) channels can include streams that the segment's final
 * talkgroup isn't aliased for (issues #2589, #2233): the segment keeps the broadcast channels of every identifier
 * that was ever added to it, and is uploaded with its final talkgroup.
 *
 * These tests document the current behavior that the stream diagnostics log investigates.
 */
public class AudioSegmentStreamingTest
{
    private static AliasList aliasList()
    {
        AliasList aliasList = new AliasList("test");

        Alias streamed = new Alias("Streamed TG 100");
        streamed.addAliasID(new Talkgroup(Protocol.APCO25, 100));
        BroadcastChannel channel = new BroadcastChannel();
        channel.setChannelName("TLR");
        streamed.addAliasID(channel);
        aliasList.addAlias(streamed);

        Alias notStreamed = new Alias("Not Streamed TG 200");
        notStreamed.addAliasID(new Talkgroup(Protocol.APCO25, 200));
        aliasList.addAlias(notStreamed);

        Alias streamedRadio = new Alias("Streamed Radio 1234");
        streamedRadio.addAliasID(new Radio(Protocol.APCO25, 1234));
        BroadcastChannel radioChannel = new BroadcastChannel();
        radioChannel.setChannelName("TLR");
        streamedRadio.addAliasID(radioChannel);
        aliasList.addAlias(streamedRadio);

        return aliasList;
    }

    /**
     * A segment that starts with the previous call's talkgroup (late entry, missed header) and then gets the new call's
     * talkgroup keeps the previous talkgroup's stream, and is uploaded as the new talkgroup.
     */
    @Test
    public void staleTalkgroupStreamIsKept()
    {
        AudioSegment segment = new AudioSegment(aliasList(), 0);
        segment.addIdentifiers(List.<Identifier>of(APCO25Talkgroup.create(100)));
        segment.addIdentifier(APCO25Talkgroup.create(200));

        assertEquals(200, segment.getIdentifierCollection().getToIdentifier().getValue(),
            "Final talkgroup is the new call's talkgroup");
        assertTrue(segment.hasBroadcastChannels(),
            "Segment keeps the stream of the previous talkgroup, so talkgroup 200 is streamed");
        assertTrue(segment.getBroadcastChannelSource(segment.getBroadcastChannels().iterator().next())
            .contains("(preload)"), "Diagnostics attribute the stream to the preloaded previous talkgroup");
    }

    /**
     * A radio with a streaming alias causes the call to be streamed whatever its talkgroup.
     */
    @Test
    public void radioAliasStreamsAnyTalkgroup()
    {
        AudioSegment segment = new AudioSegment(aliasList(), 0);
        segment.addIdentifier(APCO25Talkgroup.create(200));
        segment.addIdentifier(APCO25RadioIdentifier.createFrom(1234));

        assertTrue(segment.hasBroadcastChannels(),
            "Talkgroup 200 isn't streamed, but the call is streamed because of the radio's alias");
    }
}
