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

package io.github.dsheirer.gui.settings.thinline;

import io.github.dsheirer.gui.preference.thinline.ThinlineRadioPreferenceEditor;
import io.github.dsheirer.gui.settings.SettingsPage;
import io.github.dsheirer.gui.settings.SettingsPageType;
import io.github.dsheirer.settings.Settings;

/**
 * Settings page for the Thinline Radio connection (URL, key, health alerts and remote control permissions).
 */
public class ThinlineRadioSettingsPage extends SettingsPage
{
    /**
     * Constructs an instance
     * @param settings for the application
     */
    public ThinlineRadioSettingsPage(Settings settings)
    {
        super(SettingsPageType.THINLINE_RADIO, settings);
        addContent(new ThinlineRadioPreferenceEditor(settings));
    }
}
