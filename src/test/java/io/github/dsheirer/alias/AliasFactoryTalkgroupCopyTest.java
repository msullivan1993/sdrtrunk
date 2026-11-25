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

package io.github.dsheirer.alias;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.dsheirer.alias.id.talkgroup.Talkgroup;
import io.github.dsheirer.protocol.Protocol;
import org.junit.jupiter.api.Test;

/**
 * The alias editor edits copies of alias identifiers and copies them back on save, so a copy must keep every
 * talkgroup setting.
 */
public class AliasFactoryTalkgroupCopyTest
{
    @Test
    public void copyKeepsTimeslot()
    {
        Talkgroup original = new Talkgroup(Protocol.DMR, 2000);
        original.setTimeslot(2);
        original.setOverlap(true);

        Talkgroup copy = (Talkgroup)AliasFactory.copyOf(original);

        assertEquals(Protocol.DMR, copy.getProtocol());
        assertEquals(2000, copy.getValue());
        assertEquals(2, copy.getTimeslot());
        assertEquals(true, copy.overlapProperty().get());
    }

    @Test
    public void copyKeepsAnyTimeslot()
    {
        Talkgroup copy = (Talkgroup)AliasFactory.copyOf(new Talkgroup(Protocol.DMR, 2000));
        assertEquals(Talkgroup.ANY_TIMESLOT, copy.getTimeslot());
    }
}
