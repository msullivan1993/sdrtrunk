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
package io.github.dsheirer.module.decode.squelch.ctcss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

public class CTCSSDetectorFineResolutionTest
{
    private static final double SAMPLE_RATE = 8000.0;

    private static float[] tone(double frequency, int length, long offset)
    {
        float[] samples = new float[length];

        for(int i = 0; i < length; i++)
        {
            samples[i] = (float)(0.2 * Math.sin(2.0 * Math.PI * frequency * (i + offset) / SAMPLE_RATE));
        }

        return samples;
    }

    /**
     * Number of blocks out of the count where the detector is unmuted.
     */
    private static int unmutedBlocks(CTCSSDetector detector, double frequency, int blockLength, int count)
    {
        int unmuted = 0;
        long offset = 0;

        for(int x = 0; x < count; x++)
        {
            CTCSSMessage message = detector.process(tone(frequency, blockLength, offset));
            offset += blockLength;

            if(!message.getMutedStatus())
            {
                unmuted++;
            }
        }

        return unmuted;
    }

    @Test
    public void detectingCodesKeepPlaceholders()
    {
        assertEquals(CTCSSCode.STANDARD_CODES.size() + 2, CTCSSCode.DETECTING_CODES.size());
        assertTrue(CTCSSCode.DETECTING_CODES.contains(CTCSSCode.UNKNOWNL));
        assertTrue(CTCSSCode.DETECTING_CODES.contains(CTCSSCode.UNKNOWNH));
        assertFalse(CTCSSCode.STANDARD_CODES.contains(CTCSSCode.UNKNOWNL));
    }

    @Test
    public void lowestAndHighestTonesUnmute()
    {
        for(boolean fine : new boolean[]{false, true})
        {
            assertTrue(unmutedBlocks(new CTCSSDetector(List.of(CTCSSCode.TONE_670), fine), 67.0, 512, 10) > 0);
            assertTrue(unmutedBlocks(new CTCSSDetector(List.of(CTCSSCode.TONE_2541), fine), 254.1, 512, 10) > 0);
        }
    }

    @Test
    public void refinesBackFromSeveralTonesAway()
    {
        CTCSSDetector detector = new CTCSSDetector(List.of(CTCSSCode.TONE_915), true);
        long offset = 0;

        for(int block = 0; block < 10; block++)
        {
            float[] samples = new float[512];

            for(int i = 0; i < samples.length; i++)
            {
                double t = (i + offset) / SAMPLE_RATE;
                samples[i] = (float)(0.2 * Math.sin(2.0 * Math.PI * 91.5 * t));

                //A burst at 100 Hz in one block pulls the per-block detection several tones above 91.5
                if(block == 6)
                {
                    samples[i] += (float)(0.35 * Math.sin(2.0 * Math.PI * 100.0 * t));
                }
            }

            offset += samples.length;
            CTCSSMessage message = detector.process(samples);

            if(block >= 2)
            {
                assertEquals(CTCSSCode.TONE_915, message.getCTCSSCode(), "block " + block);
                assertFalse(message.getMutedStatus(), "block " + block);
            }

            if(block == 6)
            {
                assertEquals(CTCSSCode.TONE_915, message.getRefinedCode());
            }
        }
    }

    @Test
    public void followsToneChangeWithoutDelay()
    {
        CTCSSDetector detector = new CTCSSDetector(List.of(CTCSSCode.TONE_1413), true);
        long offset = 0;

        for(int block = 0; block < 8; block++)
        {
            assertTrue(detector.process(tone(100.0, 512, offset)).getMutedStatus());
            offset += 512;
        }

        //Unmutes after the open threshold, the same as without fine resolution
        assertTrue(detector.process(tone(141.3, 512, offset)).getMutedStatus());
        offset += 512;
        assertTrue(detector.process(tone(141.3, 512, offset)).getMutedStatus());
        offset += 512;
        assertFalse(detector.process(tone(141.3, 512, offset)).getMutedStatus());
    }

    @Test
    public void refinesWithLargerBlocks()
    {
        assertTrue(unmutedBlocks(new CTCSSDetector(List.of(CTCSSCode.TONE_915), true), 91.5, 2048, 8) > 0);
    }

    @Test
    public void shortBufferReturnsNull()
    {
        assertNull(new CTCSSDetector(List.of(CTCSSCode.TONE_915), true).process(new float[256]));
    }
}
