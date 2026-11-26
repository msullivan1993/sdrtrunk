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

import io.github.dsheirer.controller.channel.Channel;
import io.github.dsheirer.module.decode.nbfm.DecodeConfigNBFM;
import org.junit.jupiter.api.Test;

/**
 * Bandwidth of NBFM channels created from Radio Reference conventional frequencies.
 */
public class RadioReferenceChannelFactoryNarrowbandTest
{
    private static DecodeConfigNBFM.Bandwidth bandwidth(ModeDecoderType mode, long frequency, boolean unitedStates)
    {
        Channel channel = RadioReferenceChannelFactory.conventional(mode, frequency, "System", "Site", "Name",
            unitedStates);
        return ((DecodeConfigNBFM)channel.getDecodeConfiguration()).getBandwidth();
    }

    @Test
    public void usWidebandListingInMandatedBandIsNarrowband()
    {
        assertEquals(DecodeConfigNBFM.Bandwidth.BW_12_5, bandwidth(ModeDecoderType.FM, 155_085_000L, true));
    }

    @Test
    public void usMarineStaysWideband()
    {
        assertEquals(DecodeConfigNBFM.Bandwidth.BW_25_0, bandwidth(ModeDecoderType.FM, 156_800_000L, true));
    }

    @Test
    public void narrowbandListingIsNarrowband()
    {
        assertEquals(DecodeConfigNBFM.Bandwidth.BW_12_5, bandwidth(ModeDecoderType.FMN, 156_800_000L, true));
        assertEquals(DecodeConfigNBFM.Bandwidth.BW_12_5, bandwidth(ModeDecoderType.FMN, 155_085_000L, false));
    }

    @Test
    public void nonUsWidebandListingStaysWideband()
    {
        assertEquals(DecodeConfigNBFM.Bandwidth.BW_25_0, bandwidth(ModeDecoderType.FM, 155_085_000L, false));
    }
}
