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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class RadioReferenceNarrowbandTest
{
    @Test
    public void landMobileIsNarrowband()
    {
        assertTrue(RadioReferenceNarrowband.isNarrowband(155_085_000L));   //VHF public safety
        assertTrue(RadioReferenceNarrowband.isNarrowband(154_822_500L));   //VHF fire
        assertTrue(RadioReferenceNarrowband.isNarrowband(160_215_000L));   //AAR railroad
        assertTrue(RadioReferenceNarrowband.isNarrowband(161_565_000L));   //AAR railroad
        assertTrue(RadioReferenceNarrowband.isNarrowband(151_820_000L));   //MURS narrowband channel
        assertTrue(RadioReferenceNarrowband.isNarrowband(462_562_500L));   //FRS/GMRS interstitial
        assertTrue(RadioReferenceNarrowband.isNarrowband(467_712_500L));   //FRS/GMRS interstitial
        assertTrue(RadioReferenceNarrowband.isNarrowband(453_100_000L));   //UHF
        assertTrue(RadioReferenceNarrowband.isNarrowband(483_000_000L));   //T-band
        assertTrue(RadioReferenceNarrowband.isNarrowband(170_000_000L));   //federal VHF
        assertTrue(RadioReferenceNarrowband.isNarrowband(139_000_000L));   //federal VHF
        assertTrue(RadioReferenceNarrowband.isNarrowband(149_000_000L));   //federal VHF
        assertTrue(RadioReferenceNarrowband.isNarrowband(410_000_000L));   //federal UHF
        assertTrue(RadioReferenceNarrowband.isNarrowband(425_000_000L));   //Part 90 421-430 MHz
        assertTrue(RadioReferenceNarrowband.isNarrowband(157_450_000L));   //first Part 90 channel above marine
    }

    @Test
    public void widebandExceptions()
    {
        assertFalse(RadioReferenceNarrowband.isNarrowband(156_800_000L));  //marine channel 16
        assertFalse(RadioReferenceNarrowband.isNarrowband(157_100_000L));  //marine channel 22A
        assertFalse(RadioReferenceNarrowband.isNarrowband(157_425_000L));  //marine channel 88A
        assertFalse(RadioReferenceNarrowband.isNarrowband(161_975_000L));  //AIS
        assertFalse(RadioReferenceNarrowband.isNarrowband(162_550_000L));  //NOAA weather
        assertFalse(RadioReferenceNarrowband.isNarrowband(154_570_000L));  //MURS blue dot
        assertFalse(RadioReferenceNarrowband.isNarrowband(154_600_000L));  //MURS green dot
        assertFalse(RadioReferenceNarrowband.isNarrowband(462_675_000L));  //GMRS 675
        assertFalse(RadioReferenceNarrowband.isNarrowband(467_675_000L));  //GMRS repeater input
        assertFalse(RadioReferenceNarrowband.isNarrowband(462_550_000L));  //GMRS 550
        assertFalse(RadioReferenceNarrowband.isNarrowband(462_725_000L));  //GMRS 725
    }

    @Test
    public void outsideMandatedBands()
    {
        assertFalse(RadioReferenceNarrowband.isNarrowband(146_520_000L));  //2 m amateur
        assertFalse(RadioReferenceNarrowband.isNarrowband(446_000_000L));  //70 cm amateur
        assertFalse(RadioReferenceNarrowband.isNarrowband(420_500_000L));  //70 cm amateur below 421 MHz
        assertFalse(RadioReferenceNarrowband.isNarrowband(434_000_000L));  //70 cm amateur above 430 MHz
        assertFalse(RadioReferenceNarrowband.isNarrowband(136_000_000L));  //below 138 MHz
        assertFalse(RadioReferenceNarrowband.isNarrowband(851_012_500L));  //800 MHz
        assertFalse(RadioReferenceNarrowband.isNarrowband(118_800_000L));  //aviation (AM)
    }

    @Test
    public void unitedStatesCountry()
    {
        assertTrue(RadioReferenceNarrowband.isUnitedStates("US", null));
        assertTrue(RadioReferenceNarrowband.isUnitedStates(null, "United States"));
        assertFalse(RadioReferenceNarrowband.isUnitedStates("CA", "Canada"));
        assertFalse(RadioReferenceNarrowband.isUnitedStates(null, null));
    }

    @Test
    public void unitedStatesLocation()
    {
        assertTrue(RadioReferenceNarrowband.isUnitedStatesLocation(38.48, -82.64));     //Ashland, KY
        assertFalse(RadioReferenceNarrowband.isUnitedStatesLocation(43.65, -79.38));    //Toronto, ON
        assertFalse(RadioReferenceNarrowband.isUnitedStatesLocation(null, -82.64));
    }
}
