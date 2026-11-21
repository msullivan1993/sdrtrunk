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
package io.github.dsheirer.module.decode.fleetsync2.identifier;

import io.github.dsheirer.identifier.Role;
import io.github.dsheirer.identifier.radio.RadioIdentifier;
import io.github.dsheirer.protocol.Protocol;

/**
 * Fleetsync II calling unit (fleet and ident) as a radio identifier, so that the unit of a Fleetsync PTT ID (ANI) is
 * attached to the call as the calling radio, the same way that MDC-1200, P25, DMR and NXDN radio IDs are, and is
 * reported by the streaming broadcasters.
 *
 * The value is the fleet number followed by the four digit ident: fleet 100 ident 1234 has value 1001234 and is
 * displayed as 100-1234.  Fleets are 100-354 and idents are 1000-5094 as transmitted, so the value is unique and
 * reads the same as the fleet and ident programmed in the radio.
 */
public class FleetsyncRadioIdentifier extends RadioIdentifier
{
    private static final int FLEET_MULTIPLIER = 10000;

    /**
     * Constructs an instance
     * @param value of the fleet times 10,000 plus the ident
     * @param role of the identifier
     */
    public FleetsyncRadioIdentifier(Integer value, Role role)
    {
        super(value, role);
    }

    @Override
    public Protocol getProtocol()
    {
        return Protocol.FLEETSYNC;
    }

    /**
     * Fleet number
     */
    public int getFleet()
    {
        return getValue() / FLEET_MULTIPLIER;
    }

    /**
     * Unit ident within the fleet
     */
    public int getIdent()
    {
        return getValue() % FLEET_MULTIPLIER;
    }

    /**
     * Fleet and ident formatted as FFF-IIII
     */
    @Override
    public String toString()
    {
        return String.format("%03d-%04d", getFleet(), getIdent());
    }

    /**
     * Creates a FROM (calling radio) identifier.
     * @param fleet number
     * @param ident within the fleet
     */
    public static FleetsyncRadioIdentifier createFrom(int fleet, int ident)
    {
        return new FleetsyncRadioIdentifier((fleet * FLEET_MULTIPLIER) + ident, Role.FROM);
    }
}
