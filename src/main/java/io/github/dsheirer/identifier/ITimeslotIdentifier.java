/*
 * *****************************************************************************
 * Copyright (C) 2014-2024 Dennis Sheirer
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

package io.github.dsheirer.identifier;

/**
 * Identifier that records the timeslot it was decoded on, for protocols where the same identifier value can mean
 * different things on each timeslot (e.g. the same DMR talkgroup used for different purposes on each timeslot of a
 * conventional repeater).  The timeslot doesn't change how identifiers are compared: it's used for alias matching.
 *
 * Timeslot collections (identifier collections constructed for a timeslot) tag identifiers that implement this
 * interface with the collection's timeslot as they're added.
 */
public interface ITimeslotIdentifier
{
    /**
     * Timeslot the identifier was decoded on, or 0 when unknown.
     */
    int getTimeslot();

    /**
     * Copy of this identifier tagged with the timeslot, or this identifier when it's already tagged with it.
     * @param timeslot to tag
     * @return identifier tagged with the timeslot
     */
    Identifier withTimeslot(int timeslot);

    /**
     * Tags the identifier with the timeslot when it records timeslots and the timeslot is known.
     * @param identifier to tag
     * @param timeslot of the collection, 0 when the collection has no timeslot
     * @return tagged identifier, or the identifier unchanged
     */
    static Identifier tag(Identifier identifier, int timeslot)
    {
        if(timeslot > 0 && identifier instanceof ITimeslotIdentifier timeslotIdentifier &&
            timeslotIdentifier.getTimeslot() != timeslot)
        {
            return timeslotIdentifier.withTimeslot(timeslot);
        }

        return identifier;
    }
}
