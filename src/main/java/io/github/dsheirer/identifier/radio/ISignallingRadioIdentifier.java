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
package io.github.dsheirer.identifier.radio;

/**
 * Calling radio identified by in-band analog signalling (e.g. an MDC-1200 or Fleetsync PTT ID or emergency) on an
 * analog channel.  The audio module keeps the first signalling calling radio identified during a call, unless a later
 * one is an emergency, which replaces it.
 */
public interface ISignallingRadioIdentifier
{
    /**
     * Indicates if the calling radio was identified by an emergency message.
     */
    boolean isEmergency();
}
