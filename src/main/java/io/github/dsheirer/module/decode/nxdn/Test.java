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

package io.github.dsheirer.module.decode.nxdn;

import io.github.dsheirer.gui.viewer.sync.FMSyncResultsViewer;
import io.github.dsheirer.module.decode.nxdn.layer1.sync.NXDNSyncDetector;
import io.github.dsheirer.module.decode.nxdn.layer1.sync.ReferenceSyncWaveform;
import io.github.dsheirer.module.decode.nxdn.layer1.sync.standard.NXDNStandardHardSyncDetector;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

public class Test
{

    static void main()
    {
        System.out.println("Starting ...");
        DecimalFormat df = new DecimalFormat("0.00");

        NXDNSyncDetector sd = new NXDNStandardHardSyncDetector();
        float[] referencePoints = new float[sd.getSyncSymbols().length + 2];
        System.arraycopy(sd.getSyncSymbols(), 0, referencePoints, 1, sd.getSyncSymbols().length);
        System.out.println("Sync:" + Arrays.toString(referencePoints));

        double[] x = new double[referencePoints.length];
        double[] y = new double[referencePoints.length];

        for(int i = 0; i < referencePoints.length; i++)
        {
            y[i] = referencePoints[i];
            x[i] = i;
        }

        SplineInterpolator interpolator = new SplineInterpolator();
        PolynomialSplineFunction function = interpolator.interpolate(x, y);

        StringBuilder sb = new StringBuilder();

        float[] samples = new float[x.length * 10];
        float[] sync = new float[sd.getSyncSymbols().length + 1];
        int syncPointer = 0;

        for(int counter = 0; counter < x.length * 10; counter++)
        {
            try
            {
                double answer = function.value(counter / 10.0);
                samples[counter] = (float) answer;
                sb.append(df.format(answer)).append(",");
            }
            catch(Exception e)
            {
            }

            if(counter > 0 && counter % 10 == 0)
            {
                sync[syncPointer++] = (float)(counter / 10.0);
            }
        }

        FMSyncResultsViewer viewer = new FMSyncResultsViewer();
        CountDownLatch latch = new CountDownLatch(1);
        ReferenceSyncWaveform samples2 = sd.getReferenceWaveform(5.208);
        viewer.receive(sd.getSyncSymbols(), sd.getSyncSymbols(), samples2.waveform(), sync, 0, 0, "Test", latch);

        try
        {
            latch.await();
        }
        catch(InterruptedException e)
        {
            throw new RuntimeException(e);
        }

        System.out.println(sb.toString());

        System.out.println("Finished!");
    }
}
