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

import io.github.dsheirer.bits.CorrectedBinaryMessage;
import io.github.dsheirer.dsp.symbol.Dibit;
import io.github.dsheirer.message.IMessage;
import io.github.dsheirer.message.SyncLossMessage;
import io.github.dsheirer.module.decode.nxdn.layer1.Frame;
import io.github.dsheirer.module.decode.nxdn.layer1.sync.NXDNHardSyncDetector;
import io.github.dsheirer.module.decode.nxdn.layer1.sync.NXDNSyncDetector;
import io.github.dsheirer.module.decode.nxdn.layer2.LICHTracker;
import io.github.dsheirer.module.decode.nxdn.layer3.type.TransmissionMode;
import io.github.dsheirer.protocol.Protocol;
import io.github.dsheirer.sample.Listener;
import java.util.Arrays;

/**
 * Provides message framing for the demodulated dibit stream.  This framer is notified by an external sync detection
 * process using the two syncDetected() methods below to indicate if the NID that follows the sync was correctly error
 * detected and corrected.  When the NID does not pass error correction, we use a PLACEHOLDER data unit ID to allow the
 * uncertain message to assemble and then we'll inspect before and after data unit IDs and the quantity of captured
 * dibits to make a best guess on what the assembled message represents.
 */
public class NXDNMessageFramer
{
    private static final int FRAME_LENGTH = 192;
    private static final int SYNC_LENGTH = 10;
    private static final int PAYLOAD_LENGTH = FRAME_LENGTH - SYNC_LENGTH;
    private static final int SYNC_DETECTION_BIT_ERROR_THRESHOLD = 8;
    private final Dibit[] mDibitBuffer = new Dibit[192];
    private final LICHTracker mLICHTracker;
    private final Listener<IMessage> mMessageListener;
    private final NXDNHardSyncDetector mHardSyncDetector = new NXDNHardSyncDetector();
    private final int mSymbolsPerSecond;
    private boolean mSyncDetected = false;
    private int mDibitBufferPointer = 0;
    private int mDibitSinceTimestampCounter = 0;
    private int mDibitsSinceLastSync = FRAME_LENGTH;
    private long mReferenceTimestamp = 0;

    /**
     * Constructs an instance
     * @param messageListener to receive framed messages
     * @param transmissionMode to indicate how many symbols per second for accounting
     */
    public NXDNMessageFramer(Listener<IMessage> messageListener, TransmissionMode transmissionMode)
    {
        mMessageListener = messageListener;
        mLICHTracker = new LICHTracker(transmissionMode);
        mSymbolsPerSecond = transmissionMode.getSymbolRate();
        Arrays.fill(mDibitBuffer, Dibit.D00_PLUS_1);
    }

    /**
     * Sets or updates the current dibit stream time from an incoming sample buffer.
     * @param time to use as a reference timestamp.
     */
    public void setTimestamp(long time)
    {
        mReferenceTimestamp = time;
        mDibitSinceTimestampCounter = 0;
    }


    /**
     * Calculates the timestamp accurate to the currently received dibit.
     * @return timestamp in milliseconds.
     */
    private long getTimestamp()
    {
        if(mReferenceTimestamp > 0)
        {
            return mReferenceTimestamp + (long)(1000.0 * mDibitSinceTimestampCounter / 4800);
        }
        else
        {
            mDibitSinceTimestampCounter = 0;
            return System.currentTimeMillis();
        }
    }

    /**
     * Dispatches a currently assembling message once it's complete.
     * @return true if any of the messages from the frame are valid.
     */
    private boolean dispatchMessage()
    {
        CorrectedBinaryMessage cbm = new CorrectedBinaryMessage(PAYLOAD_LENGTH * 2);
        //The buffer pointer is the next symbol after the completed message, ie start of sync
        int bufferDibitPointer = mDibitBufferPointer + SYNC_LENGTH;
        bufferDibitPointer %= FRAME_LENGTH;
        int messageDibitPointer = 0;

        Dibit dibit;
        while(messageDibitPointer < PAYLOAD_LENGTH)
        {
            dibit = mDibitBuffer[bufferDibitPointer++];
            cbm.add(dibit.getBit1(), dibit.getBit2());
            bufferDibitPointer %= FRAME_LENGTH;
            messageDibitPointer++;
        }

        boolean validMessage = false;
        Frame frame = new Frame(cbm, getTimestamp(), mLICHTracker);

        for(NXDNMessage message: frame.getMessages())
        {
            validMessage |= message.isValid();
            dispatch(message);
        }

        return validMessage;
    }

    /**
     * Tests if the next 10 symbols in the dibit buffer match the sync pattern.  This uses the
     * buffer wrapping to determine if the just collected dibit sync sequence was preceded by
     * a valid sync sequence, even though the sync detector didn't trigger on the sync pattern.
     *
     * @return bit errors for the received dibit sequence against the sync pattern
     */
    private int checkSync()
    {
        long value = 0;
        int dibitBufferPointer = mDibitBufferPointer;

        for(int x = 0; x < SYNC_LENGTH; x++)
        {
            value <<= 2;
            value += mDibitBuffer[dibitBufferPointer++].getValue();
            dibitBufferPointer %= PAYLOAD_LENGTH;
        }

        return Long.bitCount(value ^ NXDNSyncDetector.SYNC_PATTERN);
    }

    /**
     * Dispatches the message to an optional listener
     * @param message to dispatch
     */
    private void dispatch(IMessage message)
    {
        if(mMessageListener != null)
        {
            mMessageListener.receive(message);
        }
    }

    public void syncDetected()
    {
        mSyncDetected = true;

        //Update sync loss accounting when we have more/less than the expected sync pattern symbol count.
        if(mDibitsSinceLastSync > SYNC_LENGTH)
        {
            dispatch(new SyncLossMessage(getTimestamp(),
                    (mDibitsSinceLastSync - SYNC_LENGTH) * 2, Protocol.NXDN));
            mDibitsSinceLastSync = SYNC_LENGTH;
        }
        else if(mDibitsSinceLastSync != SYNC_LENGTH)
        {
            mDibitsSinceLastSync = SYNC_LENGTH;
        }
    }

    /**
     * Process hard symbol decision without sync detection.
     * @param symbol that was demodulated.
     */
    public boolean process(Dibit symbol)
    {
        mDibitBuffer[mDibitBufferPointer++] = symbol;
        mDibitBufferPointer %= FRAME_LENGTH;
        mDibitsSinceLastSync++;
        mDibitSinceTimestampCounter++;

        if(mDibitsSinceLastSync == FRAME_LENGTH)
        {
            if(mSyncDetected)
            {
                mSyncDetected = false;
                boolean valid = dispatchMessage();
                mDibitsSinceLastSync = 0;
                return valid;
            }
            else
            {
                int bitErrors = checkSync();

                if(bitErrors <= SYNC_DETECTION_BIT_ERROR_THRESHOLD)
                {
                    //Note: we don't reset mDibitsSinceLastSync here since we forced a sync detection so that we don't
                    //have a continuous stream of bad messages.  We do it just once to in between valid sync detections.
                    return dispatchMessage();
                }
            }
        }
        else if(mDibitsSinceLastSync > mSymbolsPerSecond)
        {
            mDibitsSinceLastSync -= mSymbolsPerSecond;
            dispatch(new SyncLossMessage(getTimestamp(), mSymbolsPerSecond * 2, Protocol.NXDN));
        }

        return false;
    }

    /**
     * Process symbol decision with (hard) sync detection
     * @param symbol to process
     */
    public void processWithHardSyncDetection(Dibit symbol)
    {
        process(symbol);

        if(mHardSyncDetector.process(symbol))
        {
            syncDetected();
        }
        else if(mDibitsSinceLastSync == FRAME_LENGTH)
        {
            if(mHardSyncDetector.getCurrentBitErrors() <= SYNC_DETECTION_BIT_ERROR_THRESHOLD)
            {
                syncDetected();
            }
        }
    }
}
