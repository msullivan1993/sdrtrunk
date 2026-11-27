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

package io.github.dsheirer.gui.playlist.streaming;

import io.github.dsheirer.audio.broadcast.BroadcastServerType;
import io.github.dsheirer.playlist.PlaylistManager;

/**
 * Thinline Radio Core stream editor. The site key lives in User Preferences, not on the stream.
 */
public class ThinlineRadioCoreEditor extends RdioScannerEditor
{
    private static final String SUMMARY = "Uses the site key from User Preferences. Calls stay on this site until ThinLine asks for one.";
    private static final String UPLOAD_PATH = "/api/call-upload";
    private static final String MISSING_KEY = "Open User Preferences → Thinline Radio and enter the site key before enabling this stream.";

    public ThinlineRadioCoreEditor(PlaylistManager playlistManager)
    {
        super(playlistManager);
    }

    @Override
    public BroadcastServerType getBroadcastServerType()
    {
        return BroadcastServerType.THINLINE_RADIO_CORE;
    }

    @Override
    protected String getHostLabel()
    {
        return "Thinline Radio URL";
    }

    @Override
    protected boolean showsApiKey()
    {
        return false;
    }

    @Override
    public void setItem(io.github.dsheirer.audio.broadcast.rdioscanner.RdioScannerConfiguration item)
    {
        //A new stream has the default localhost host: fill in the Thinline Radio URL from User Preferences on the
        //stream configuration itself, before the fields are loaded.  Changing only the displayed host (and then
        //clearing the modified flag) made the streaming editor reload the stream, which reloaded the stored localhost
        //host and repeated until the stack overflowed.
        if(item != null)
        {
            String preferenceUrl = getPlaylistManager().getSettings().getThinlineRadioPreference().getUrl();
            String host = item.getHost();

            if(preferenceUrl != null && !preferenceUrl.isBlank() &&
                (host == null || host.isBlank() || host.startsWith("http://localhost")))
            {
                String base = preferenceUrl.trim();

                if(base.endsWith(UPLOAD_PATH))
                {
                    base = base.substring(0, base.length() - UPLOAD_PATH.length());
                }

                item.setHost(base + UPLOAD_PATH);
            }
        }

        super.setItem(item);

        if(item != null)
        {
            //Enable the stream only after the site key is entered in User Preferences
            getEnabledSwitch().setDisable(!hasSiteKey());
        }
    }
}
