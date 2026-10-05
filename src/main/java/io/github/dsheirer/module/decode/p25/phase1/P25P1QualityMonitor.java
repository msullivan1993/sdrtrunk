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

package io.github.dsheirer.module.decode.p25.phase1;

import io.github.dsheirer.controller.channel.Channel;
import io.github.dsheirer.message.DroppedSamplesMessage;
import io.github.dsheirer.message.IMessage;
import io.github.dsheirer.message.SyncLossMessage;
import io.github.dsheirer.module.decode.p25.phase1.message.P25P1Message;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unattended decode-quality instrumentation for a P25 Phase 1 channel.  Watches the decoded message stream and logs:
 *
 * - An immediate WARN for each mid-call voice dropout >= 1 s and each control channel outage >= 10 s.
 * - A periodic summary per channel (conventional/control) or per system (all traffic channels of a system combined).
 *
 * All lines are prefixed [P25STATS] for grep/log routing.  Summary interval defaults to 15 minutes and can be set with
 * -Dp25.stats.minutes=N.  Message timestamps come from the framer and are accurate to the dibit, so LDU spacing
 * (nominally 180 ms) gives a direct measure of voice frames lost mid-call.
 */
public class P25P1QualityMonitor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(P25P1QualityMonitor.class);
    private static final Map<String,Counters> COUNTERS = new ConcurrentHashMap<>();
    private static final long INTERVAL_MS = Math.max(1, Long.getLong("p25.stats.minutes", 15)) * 60_000L;
    private static final long LDU_SPACING_MS = 180;
    private static final long LDU_GAP_THRESHOLD_MS = 270;     //Gap >= 1.5 LDU periods means at least one LDU lost
    private static final long CALL_BREAK_MS = 5000;           //Gap longer than this is treated as a new call
    private static final long VOICE_DROPOUT_WARN_MS = 1000;
    private static final long CONTROL_OUTAGE_MIN_MS = 2000;   //Counted as an outage
    private static final long CONTROL_OUTAGE_WARN_MS = 10_000;
    private static final double MS_PER_SYNC_LOSS_BIT = 1000.0 / 9600.0;

    private final String mLabel;
    private final Counters mCounters;

    //Per-instance tracking state
    private long mLastLDUTimestamp;
    private boolean mTerminatedSinceLastLDU = true;
    private boolean mControlChannel;
    private long mSyncLossBits;

    public P25P1QualityMonitor(Channel channel)
    {
        String label = "P25P1";

        if(channel != null)
        {
            if(channel.isTrafficChannel())
            {
                label = channel.getSystem() + " [traffic]";
            }
            else
            {
                label = channel.getName();
            }
        }

        mLabel = label;
        mCounters = COUNTERS.computeIfAbsent(mLabel, k -> new Counters());
    }

    /**
     * Processes every message received by the decoder state.
     */
    public void receive(IMessage message)
    {
        if(message instanceof SyncLossMessage syncLoss)
        {
            mSyncLossBits += Math.max(syncLoss.getBitsProcessed(), 0);
        }
        else if(message instanceof DroppedSamplesMessage)
        {
            mCounters.realign();
        }
        else if(message instanceof P25P1Message p1)
        {
            endSyncLossRun();

            switch(p1.getDUID())
            {
                case LOGICAL_LINK_DATA_UNIT_1:
                case LOGICAL_LINK_DATA_UNIT_2:
                    processLDU(p1.getTimestamp());
                    break;
                case HEADER_DATA_UNIT:
                    if(mLastLDUTimestamp > 0 && !mTerminatedSinceLastLDU)
                    {
                        mCounters.unterminated();
                    }
                    mTerminatedSinceLastLDU = true;
                    break;
                case TRUNKING_SIGNALING_BLOCK_1:
                case TRUNKING_SIGNALING_BLOCK_2:
                case TRUNKING_SIGNALING_BLOCK_3:
                    mControlChannel = true;
                    mCounters.tsbk();
                    break;
                default:
                    break;
            }
        }

        mCounters.maybeLogSummary(mLabel);
    }

    /**
     * Decoder state committed a call-ending TDU or valid TDULC.
     */
    public void terminator()
    {
        mTerminatedSinceLastLDU = true;
        mCounters.terminator();
    }

    /**
     * Decoder state discarded a mid-voice phantom TDU.
     */
    public void phantomTDU()
    {
        mCounters.phantom();
    }

    private void processLDU(long timestamp)
    {
        if(mLastLDUTimestamp == 0 || mTerminatedSinceLastLDU)
        {
            mCounters.call();
        }
        else
        {
            long gap = timestamp - mLastLDUTimestamp;

            if(gap > CALL_BREAK_MS)
            {
                mCounters.unterminated(); //Previous call faded out with no terminator
                mCounters.call();
            }
            else if(gap >= LDU_GAP_THRESHOLD_MS)
            {
                int lost = (int)Math.max(1, Math.round((double)gap / LDU_SPACING_MS) - 1);
                mCounters.voiceGap(gap, lost);

                if(gap >= VOICE_DROPOUT_WARN_MS)
                {
                    LOGGER.warn(String.format(Locale.US, "[P25STATS] %s voice dropout %.1fs mid-call (~%d LDU lost)",
                            mLabel, gap / 1000.0, lost));
                }
            }
        }

        mCounters.ldu();
        mLastLDUTimestamp = timestamp;
        mTerminatedSinceLastLDU = false;
    }

    private void endSyncLossRun()
    {
        if(mSyncLossBits > 0)
        {
            long ms = Math.round(mSyncLossBits * MS_PER_SYNC_LOSS_BIT);
            mSyncLossBits = 0;

            if(mControlChannel && ms >= CONTROL_OUTAGE_MIN_MS)
            {
                mCounters.outage(ms);

                if(ms >= CONTROL_OUTAGE_WARN_MS)
                {
                    LOGGER.warn(String.format(Locale.US, "[P25STATS] %s control channel outage %.1fs ended",
                            mLabel, ms / 1000.0));
                }
            }
        }
    }

    /**
     * Interval counters, shared per label.  Synchronized because traffic channels of one system run concurrently.
     */
    private static class Counters
    {
        private long mIntervalStart = System.currentTimeMillis();
        private long mCalls, mLDU, mLostLDU, mGaps, mMaxGapMs, mUnterminated, mTerminators, mPhantom, mRealign;
        private long mTSBK, mOutages, mDeadMs, mMaxDeadMs;

        synchronized void call() { mCalls++; }
        synchronized void ldu() { mLDU++; }
        synchronized void unterminated() { mUnterminated++; }
        synchronized void terminator() { mTerminators++; }
        synchronized void phantom() { mPhantom++; }
        synchronized void realign() { mRealign++; }
        synchronized void tsbk() { mTSBK++; }

        synchronized void voiceGap(long gapMs, int lost)
        {
            mGaps++;
            mLostLDU += lost;
            mMaxGapMs = Math.max(mMaxGapMs, gapMs);
        }

        synchronized void outage(long ms)
        {
            mOutages++;
            mDeadMs += ms;
            mMaxDeadMs = Math.max(mMaxDeadMs, ms);
        }

        synchronized void maybeLogSummary(String label)
        {
            long now = System.currentTimeMillis();

            if(now - mIntervalStart < INTERVAL_MS)
            {
                return;
            }

            if(mLDU > 0 || mTSBK > 0 || mPhantom > 0 || mUnterminated > 0)
            {
                double lossPct = (mLDU + mLostLDU) > 0 ? 100.0 * mLostLDU / (mLDU + mLostLDU) : 0.0;
                StringBuilder sb = new StringBuilder();
                sb.append(String.format(Locale.US, "[P25STATS] %s %dm: calls=%d ldu=%d lostLdu=%d (%.2f%%) gaps=%d " +
                        "maxGap=%.1fs unterminated=%d terminators=%d phantomTDU=%d realign=%d", label,
                        (now - mIntervalStart) / 60_000, mCalls, mLDU, mLostLDU, lossPct, mGaps, mMaxGapMs / 1000.0,
                        mUnterminated, mTerminators, mPhantom, mRealign));

                if(mTSBK > 0)
                {
                    double deadPct = 100.0 * mDeadMs / (now - mIntervalStart);
                    sb.append(String.format(Locale.US, " | control: tsbk=%d outages=%d dead=%.1fs (%.2f%%) longest=%.1fs",
                            mTSBK, mOutages, mDeadMs / 1000.0, deadPct, mMaxDeadMs / 1000.0));
                }

                LOGGER.info(sb.toString());
            }

            mIntervalStart = now;
            mCalls = mLDU = mLostLDU = mGaps = mMaxGapMs = mUnterminated = mTerminators = mPhantom = mRealign = 0;
            mTSBK = mOutages = mDeadMs = mMaxDeadMs = 0;
        }
    }
}
