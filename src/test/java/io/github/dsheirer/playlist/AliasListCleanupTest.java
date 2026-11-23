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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.dsheirer.alias.Alias;
import io.github.dsheirer.alias.AliasModel;
import io.github.dsheirer.alias.id.talkgroup.StreamAsTalkgroup;
import io.github.dsheirer.alias.id.talkgroup.Talkgroup;
import io.github.dsheirer.alias.id.talkgroup.TalkgroupRange;
import io.github.dsheirer.controller.channel.Channel;
import io.github.dsheirer.module.decode.dmr.DecodeConfigDMR;
import io.github.dsheirer.module.decode.nbfm.DecodeConfigNBFM;
import io.github.dsheirer.protocol.Protocol;
import java.util.List;
import org.junit.jupiter.api.Test;

public class AliasListCleanupTest
{
    private static Channel nbfm(String name, String list, int talkgroup)
    {
        Channel channel = new Channel(name);
        channel.setAliasListName(list);
        DecodeConfigNBFM config = new DecodeConfigNBFM();
        config.setTalkgroup(talkgroup);
        channel.setDecodeConfiguration(config);
        return channel;
    }

    private static Alias alias(String name, String list, io.github.dsheirer.alias.id.AliasID... ids)
    {
        Alias alias = new Alias(name);
        alias.setAliasListName(list);
        for(io.github.dsheirer.alias.id.AliasID id : ids)
        {
            alias.addAliasID(id);
        }
        return alias;
    }

    @Test
    public void otherChannelsAndUnusedLists()
    {
        Channel a = nbfm("A", "County", 1);
        Channel b = nbfm("B", "County", 2);
        Channel c = nbfm("C", null, 1);
        List<Channel> channels = List.of(a, b, c);

        assertEquals(List.of(b), AliasListCleanup.getOtherChannels("County", a, channels));
        assertEquals(List.of(), AliasListCleanup.getOtherChannels(null, a, channels));
        assertEquals(List.of("Old", "zeta"), AliasListCleanup.getUnusedAliasLists(
            List.of("County", "zeta", "Old", AliasModel.NO_ALIAS_LIST), channels));
    }

    @Test
    public void channelTalkgroupAlias()
    {
        Channel a = nbfm("A", "County", 7);
        Channel b = nbfm("B", "County", 8);
        Alias a7 = alias("Louisa FD", "County", new Talkgroup(Protocol.NBFM, 7), new StreamAsTalkgroup(500));
        Alias b8 = alias("Other", "County", new Talkgroup(Protocol.NBFM, 8));
        List<Alias> aliases = List.of(a7, b8);

        assertSame(a7, AliasListCleanup.getChannelTalkgroupAlias(a, aliases, List.of(b)));

        //Another channel in the list uses the same talkgroup
        assertNull(AliasListCleanup.getChannelTalkgroupAlias(a, aliases, List.of(b, nbfm("C", "County", 7))));

        //Alias also covers other talkgroups
        Alias range = alias("Range", "County", new Talkgroup(Protocol.NBFM, 7), new TalkgroupRange(Protocol.NBFM, 20, 30));
        assertNull(AliasListCleanup.getChannelTalkgroupAlias(a, List.of(range), List.of()));

        //Decoded talkgroup channels are never matched
        Channel dmr = new Channel("DMR");
        dmr.setAliasListName("County");
        dmr.setDecodeConfiguration(new DecodeConfigDMR());
        assertNull(AliasListCleanup.getChannelTalkgroupAlias(dmr, aliases, List.of()));
    }
}
