/*
 * *****************************************************************************
 * Copyright (C) 2014-2021 Dennis Sheirer
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
package io.github.dsheirer.module.decode.mdc1200.identifier;

import io.github.dsheirer.identifier.Role;
import io.github.dsheirer.identifier.radio.ISignallingRadioIdentifier;
import io.github.dsheirer.identifier.radio.RadioIdentifier;
import io.github.dsheirer.protocol.Protocol;

/**
 * MDC-1200 unit ID as a radio identifier, so that the unit ID of an MDC-1200 PTT ID (ANI) is attached to the call as
 * the calling radio, the same way that P25, DMR and NXDN radio IDs are, and is reported by the streaming broadcasters.
 *
 * MDC-1200 unit IDs are 16-bit values that are programmed and displayed as four hex digits.  Most agencies use IDs
 * made of the digits 0-9 only (e.g. 1234), and expect to see the same digits, so getStreamingValue() reports those IDs
 * as the decimal number with the same digits (hex 0x1234 is reported as 1234, always 9999 or less).  IDs that contain
 * the hex digits A-F can't be written that way, and are reported as 10000 plus their numeric value (0x96E4 is reported
 * as 48628), so that they never collide with an ID made of the digits 0-9.
 */
public class MDC1200RadioIdentifier extends RadioIdentifier implements ISignallingRadioIdentifier
{
    private boolean mEmergency = false;

    /**
     * Constructs an instance
     * @param value of the unit ID
     * @param role of the identifier
     */
    public MDC1200RadioIdentifier(Integer value, Role role)
    {
        super(value, role);
    }

    /**
     * Constructs an instance
     * @param value of the unit ID
     * @param role of the identifier
     * @param emergency true if the unit was identified by an emergency message
     */
    public MDC1200RadioIdentifier(Integer value, Role role, boolean emergency)
    {
        super(value, role);
        mEmergency = emergency;
    }

    @Override
    public boolean isEmergency()
    {
        return mEmergency;
    }

    /**
     * Offset added to the numeric value of unit IDs that contain the hex digits A-F when reported to streaming services.
     */
    public static final int HEX_ID_STREAMING_OFFSET = 10000;

    @Override
    public Protocol getProtocol()
    {
        return Protocol.MDC1200;
    }

    /**
     * Unit ID as four hex digits, the way it is programmed in the radio.
     */
    @Override
    public String toString()
    {
        return String.format("%04X", getValue());
    }

    /**
     * Value to report to streaming services that only accept a numeric unit ID.
     * @return the decimal number with the same digits as the hex unit ID when it uses the digits 0-9 only, otherwise
     * 10000 plus the numeric value of the unit ID.
     */
    @Override
    public int getStreamingValue()
    {
        String hex = Integer.toHexString(getValue());

        if(hex.chars().allMatch(Character::isDigit))
        {
            return Integer.parseInt(hex);
        }

        return HEX_ID_STREAMING_OFFSET + getValue();
    }

    /**
     * Creates a FROM (calling radio) identifier.
     */
    public static MDC1200RadioIdentifier createFrom(int value)
    {
        return new MDC1200RadioIdentifier(value, Role.FROM);
    }

    /**
     * Creates a FROM (calling radio) identifier.
     * @param value of the unit ID
     * @param emergency true if the unit was identified by an emergency message
     */
    public static MDC1200RadioIdentifier createFrom(int value, boolean emergency)
    {
        return new MDC1200RadioIdentifier(value, Role.FROM, emergency);
    }
}
