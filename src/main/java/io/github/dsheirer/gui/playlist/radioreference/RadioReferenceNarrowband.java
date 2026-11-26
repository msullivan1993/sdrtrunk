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

import io.github.dsheirer.service.radioreference.StateBounds;
import java.util.List;

/**
 * Narrowband (12.5 kHz) correction for conventional analog FM frequencies imported from Radio Reference, for United
 * States systems only.
 *
 * Radio Reference lists many frequencies with the wideband FM mode that have been narrowband since the FCC Part 90
 * narrowbanding mandate (January 1, 2013) or the NTIA narrowbanding of federal frequencies.  These US frequency ranges
 * are created as 12.5 kHz channels:
 * <ul>
 *     <li>138-144 MHz and 148-150 MHz: federal VHF (NTIA)</li>
 *     <li>150-174 MHz: Part 90 VHF land mobile, plus federal VHF above 162.0125 MHz</li>
 *     <li>406.1-420 MHz: federal UHF (NTIA)</li>
 *     <li>421-430 MHz: Part 90 land mobile (Detroit, Cleveland and Buffalo areas)</li>
 *     <li>450-512 MHz: Part 90 UHF land mobile and T-band</li>
 * </ul>
 * The amateur 144-148 MHz, 420-421 MHz and 430-450 MHz bands aren't covered.  Within the covered ranges, these
 * services are still licensed for wideband (25 kHz) operation and keep the wideband mode:
 * <ul>
 *     <li>VHF marine (Part 80) ship and coast frequencies: 156.000-157.425 MHz and 161.575-162.025 MHz</li>
 *     <li>NOAA weather radio broadcasts: 162.400-162.550 MHz</li>
 *     <li>MURS wideband channels (Part 95): 154.570 and 154.600 MHz</li>
 *     <li>GMRS (Part 95) main and repeater input channels: 462.550-462.725 MHz and 467.550-467.725 MHz on 25 kHz
 *     steps (the interstitial FRS/GMRS channels between them are narrowband)</li>
 * </ul>
 * 800 and 900 MHz frequencies aren't covered by the mandate and keep the wideband mode.
 *
 * Other countries listed by Radio Reference (for example Canada) still license wideband FM in these bands, so their
 * frequencies keep the mode Radio Reference lists.
 */
public final class RadioReferenceNarrowband
{
    private static final long MHZ = 1_000_000L;
    private static final long KHZ = 1_000L;

    /**
     * US narrowband ranges as {start, end} in Hertz, inclusive.
     */
    private static final long[][] NARROWBAND_RANGES = {
        {138 * MHZ, 144 * MHZ},                          //Federal VHF
        {148 * MHZ, 174 * MHZ},                          //Federal VHF, Part 90 VHF land mobile
        {406_100 * KHZ, 420 * MHZ},                      //Federal UHF
        {421 * MHZ, 430 * MHZ},                          //Part 90 land mobile, Detroit / Cleveland / Buffalo
        {450 * MHZ, 512 * MHZ},                          //Part 90 UHF land mobile, T-band
    };

    /**
     * Wideband exceptions as {start, end} in Hertz, inclusive.  Exact channels are listed with a 1 kHz tolerance.
     */
    private static final long[][] WIDEBAND_EXCEPTIONS = {
        //VHF marine ship and coast, through channel 88A (157.425).  157.450 MHz is the first Part 90 channel.
        {156_000 * KHZ, 157_425 * KHZ},
        //VHF marine coast and AIS.  Marine coast duplex channels start at 160.625 MHz, but in the US the AAR railroad
        //channels (Part 90, narrowband) occupy 160.215-161.565 MHz, so the exception starts at 161.575 MHz.
        {161_575 * KHZ, 162_025 * KHZ},
        {162_400 * KHZ, 162_550 * KHZ},                  //NOAA weather radio
        {154_569 * KHZ, 154_571 * KHZ},                  //MURS 154.570 (Blue Dot)
        {154_599 * KHZ, 154_601 * KHZ},                  //MURS 154.600 (Green Dot)
    };

    /**
     * GMRS main channels (462.550-462.725 MHz) and repeater inputs (467.550-467.725 MHz) on 25 kHz steps.  The
     * interstitial channels between them (e.g. 462.5625 MHz) are narrowband FRS/GMRS channels.
     */
    private static final long[] GMRS_WIDEBAND_START = {462_550 * KHZ, 467_550 * KHZ};
    private static final long GMRS_WIDEBAND_SPAN = 175 * KHZ;
    private static final long GMRS_CHANNEL_STEP = 25 * KHZ;
    private static final long CHANNEL_TOLERANCE = 1 * KHZ;

    private static final String UNITED_STATES_CODE = "US";

    private RadioReferenceNarrowband()
    {
    }

    /**
     * Indicates if an analog FM frequency is required to be narrowband (12.5 kHz) in the United States.
     * @param frequency in Hertz
     * @return true if the frequency is in a US narrowband range and isn't a wideband exception
     */
    public static boolean isNarrowband(long frequency)
    {
        boolean inBand = false;

        for(long[] range : NARROWBAND_RANGES)
        {
            if(frequency >= range[0] && frequency <= range[1])
            {
                inBand = true;
                break;
            }
        }

        if(!inBand)
        {
            return false;
        }

        for(long[] exception : WIDEBAND_EXCEPTIONS)
        {
            if(frequency >= exception[0] && frequency <= exception[1])
            {
                return false;
            }
        }

        for(long start : GMRS_WIDEBAND_START)
        {
            long offset = frequency - start;

            if(offset >= -CHANNEL_TOLERANCE && offset <= GMRS_WIDEBAND_SPAN + CHANNEL_TOLERANCE)
            {
                long fromChannel = Math.floorMod(offset + CHANNEL_TOLERANCE, GMRS_CHANNEL_STEP);

                if(fromChannel <= 2 * CHANNEL_TOLERANCE)
                {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Indicates if a Radio Reference country is the United States.
     * @param countryCode Radio Reference country code, or null
     * @param countryName Radio Reference country name, or null
     */
    public static boolean isUnitedStates(String countryCode, String countryName)
    {
        if(countryCode != null && countryCode.trim().equalsIgnoreCase(UNITED_STATES_CODE))
        {
            return true;
        }

        return countryName != null && countryName.trim().toLowerCase().startsWith("united states");
    }

    /**
     * Indicates if a location is in the United States: every state or province near the location is a US state.
     * Locations near the Canadian border, outside North America or unknown aren't treated as United States.
     * @param latitude of the location, or null
     * @param longitude of the location, or null
     */
    public static boolean isUnitedStatesLocation(Double latitude, Double longitude)
    {
        if(latitude == null || longitude == null)
        {
            return false;
        }

        List<StateBounds> states = StateBounds.near(latitude, longitude, 0);
        return !states.isEmpty() &&
            states.stream().allMatch(state -> UNITED_STATES_CODE.equalsIgnoreCase(state.countryCode()));
    }
}
