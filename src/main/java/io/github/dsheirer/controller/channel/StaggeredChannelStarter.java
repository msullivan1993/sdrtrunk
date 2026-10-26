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
package io.github.dsheirer.controller.channel;

import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.source.config.SourceConfigTuner;
import io.github.dsheirer.source.config.SourceConfigTunerMultipleFrequency;
import io.github.dsheirer.source.config.SourceConfiguration;
import io.github.dsheirer.source.tuner.TunerController;
import io.github.dsheirer.source.tuner.manager.DiscoveredTuner;
import io.github.dsheirer.source.tuner.manager.TunerManager;
import io.github.dsheirer.util.ThreadPool;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts auto-start channels in batches, one tuner at a time, instead of all at once.
 *
 * Each channel is placed in a tuner group:
 *   1. its preferred tuner, when one is set and that tuner is available;
 *   2. otherwise the tuner whose current usable span (center +/- usable half bandwidth) contains the channel;
 *   3. otherwise an "(unassigned)" group that starts last.
 *
 * Tuner groups start in ascending order of tuner center frequency.  Within a group, channels that have an explicit
 * auto-start order start first (in that order), then the remaining channels in ascending frequency order.  A batch
 * never spans two groups.
 *
 * This only changes the order and timing of channel start requests.  The TunerManager still makes the actual tuner
 * assignment for each channel exactly as before.
 *
 * Tunable with JVM system properties:
 *   -Dsdrtrunk.autostart.batchSize=10      channels per batch (0 = start everything at once, the original behavior)
 *   -Dsdrtrunk.autostart.batchDelayMs=1000 delay between batches within a tuner group
 *   -Dsdrtrunk.autostart.groupDelayMs=3000 delay before the first batch of each new tuner group
 */
public class StaggeredChannelStarter
{
    private static final Logger mLog = LoggerFactory.getLogger(StaggeredChannelStarter.class);

    public static final String PROPERTY_BATCH_SIZE = "sdrtrunk.autostart.batchSize";
    public static final String PROPERTY_BATCH_DELAY_MS = "sdrtrunk.autostart.batchDelayMs";
    public static final String PROPERTY_GROUP_DELAY_MS = "sdrtrunk.autostart.groupDelayMs";

    private static final int DEFAULT_BATCH_SIZE = 10;
    private static final int DEFAULT_BATCH_DELAY_MS = 1000;
    private static final int DEFAULT_GROUP_DELAY_MS = 3000;
    private static final String UNASSIGNED = "(unassigned)";

    private final Listener<ChannelEvent> mListener;
    private final List<Batch> mBatches;
    private final int mTotalChannels;
    private final int mBatchDelayMs;
    private final int mGroupDelayMs;
    private int mBatchIndex = 0;
    private int mStartedCount = 0;
    private long mStartTimestamp;

    private StaggeredChannelStarter(Listener<ChannelEvent> listener, List<Batch> batches, int totalChannels,
                                    int batchDelayMs, int groupDelayMs)
    {
        mListener = listener;
        mBatches = batches;
        mTotalChannels = totalChannels;
        mBatchDelayMs = batchDelayMs;
        mGroupDelayMs = groupDelayMs;
    }

    /**
     * Plans and starts the staggered channel start.  Returns immediately; batches run on the shared scheduled
     * executor so no thread sleeps or blocks between batches.
     *
     * @param listener to receive the channel enable requests (ChannelProcessingManager)
     * @param tunerManager used to plan the tuner groups, or null to group everything as unassigned
     * @param channels to start, in the auto-start order provided by the channel model
     */
    public static void start(Listener<ChannelEvent> listener, TunerManager tunerManager, List<Channel> channels)
    {
        if(listener == null || channels == null || channels.isEmpty())
        {
            return;
        }

        int batchSize = Math.max(0, Integer.getInteger(PROPERTY_BATCH_SIZE, DEFAULT_BATCH_SIZE));
        int batchDelayMs = Math.max(0, Integer.getInteger(PROPERTY_BATCH_DELAY_MS, DEFAULT_BATCH_DELAY_MS));
        int groupDelayMs = Math.max(0, Integer.getInteger(PROPERTY_GROUP_DELAY_MS, DEFAULT_GROUP_DELAY_MS));

        if(batchSize == 0)
        {
            mLog.info("Auto-start: staggering disabled (" + PROPERTY_BATCH_SIZE + "=0) - starting [" +
                    channels.size() + "] channels at once");

            for(Channel channel : channels)
            {
                enable(listener, channel);
            }

            return;
        }

        List<TunerSpan> spans = getTunerSpans(tunerManager);
        Map<String,List<Entry>> groups = new LinkedHashMap<>();

        for(TunerSpan span : spans)
        {
            groups.put(span.name(), new ArrayList<>());
        }

        groups.put(UNASSIGNED, new ArrayList<>());

        for(int x = 0; x < channels.size(); x++)
        {
            Channel channel = channels.get(x);
            long frequency = getFrequency(channel);
            String group = assign(channel, frequency, spans);
            groups.get(group).add(new Entry(channel, frequency, x));
        }

        Comparator<Entry> order = (e1, e2) -> {
            boolean o1 = e1.channel().hasAutoStartOrder();
            boolean o2 = e2.channel().hasAutoStartOrder();

            if(o1 && o2)
            {
                return Integer.compare(e1.index(), e2.index()); //Channel model already sorted these by order
            }
            else if(o1)
            {
                return -1;
            }
            else if(o2)
            {
                return 1;
            }

            return Long.compare(e1.frequency(), e2.frequency());
        };

        List<Batch> batches = new ArrayList<>();

        for(Map.Entry<String,List<Entry>> group : groups.entrySet())
        {
            List<Entry> entries = group.getValue();

            if(entries.isEmpty())
            {
                continue;
            }

            entries.sort(order);
            logPlan(group.getKey(), spans, entries);

            for(int x = 0; x < entries.size(); x += batchSize)
            {
                List<Channel> batchChannels = new ArrayList<>();

                for(Entry entry : entries.subList(x, Math.min(x + batchSize, entries.size())))
                {
                    batchChannels.add(entry.channel());
                }

                batches.add(new Batch(group.getKey(), x == 0, batchChannels));
            }
        }

        mLog.info("Auto-start: starting [" + channels.size() + "] channels in batches of [" + batchSize +
                "] every [" + batchDelayMs + "] ms, [" + groupDelayMs + "] ms pause between tuner groups");

        StaggeredChannelStarter starter = new StaggeredChannelStarter(listener, batches, channels.size(),
                batchDelayMs, groupDelayMs);
        starter.mStartTimestamp = System.currentTimeMillis();
        ThreadPool.SCHEDULED.schedule(starter::runNextBatch, 0, TimeUnit.MILLISECONDS);
    }

    /**
     * Starts the next batch and schedules the one after it.
     */
    private void runNextBatch()
    {
        try
        {
            if(mBatchIndex >= mBatches.size())
            {
                return;
            }

            Batch batch = mBatches.get(mBatchIndex++);

            for(Channel channel : batch.channels())
            {
                enable(mListener, channel);
            }

            mStartedCount += batch.channels().size();

            mLog.info("Auto-start: batch [" + mBatchIndex + "/" + mBatches.size() + "] group [" + batch.group() +
                    "] started [" + batch.channels().size() + "] channels (" + mStartedCount + "/" + mTotalChannels + ")");

            if(mBatchIndex < mBatches.size())
            {
                long delay = mBatches.get(mBatchIndex).firstInGroup() ? mGroupDelayMs : mBatchDelayMs;
                ThreadPool.SCHEDULED.schedule(this::runNextBatch, delay, TimeUnit.MILLISECONDS);
            }
            else
            {
                long seconds = Math.round((System.currentTimeMillis() - mStartTimestamp) / 1000.0);
                mLog.info("Auto-start: complete - [" + mStartedCount + "] channels in [" + seconds + "] seconds");
            }
        }
        catch(Throwable t)
        {
            //Never let a failure strand the remaining batches
            mLog.error("Auto-start: error processing batch [" + mBatchIndex + "] - continuing with next batch", t);

            if(mBatchIndex < mBatches.size())
            {
                ThreadPool.SCHEDULED.schedule(this::runNextBatch, mBatchDelayMs, TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * Sends a single enable request, logging rather than propagating any failure.
     */
    private static void enable(Listener<ChannelEvent> listener, Channel channel)
    {
        try
        {
            listener.receive(new ChannelEvent(channel, ChannelEvent.Event.REQUEST_ENABLE));
        }
        catch(Exception e)
        {
            mLog.error("Auto-start: error starting channel [" + channel.getName() + "]", e);
        }
    }

    /**
     * Snapshot of the available tuners and their current usable spans, ordered by ascending center frequency.
     */
    private static List<TunerSpan> getTunerSpans(TunerManager tunerManager)
    {
        List<TunerSpan> spans = new ArrayList<>();

        if(tunerManager == null)
        {
            return spans;
        }

        try
        {
            for(DiscoveredTuner discoveredTuner : tunerManager.getAvailableTuners())
            {
                if(discoveredTuner.isAvailable() && discoveredTuner.hasTuner())
                {
                    String name = discoveredTuner.getTuner().getPreferredName();
                    TunerController controller = discoveredTuner.getTuner().getTunerController();
                    long center = controller.getFrequency();
                    long half = controller.getUsableHalfBandwidth();

                    boolean duplicate = false;

                    for(TunerSpan span : spans)
                    {
                        if(span.name().equalsIgnoreCase(name))
                        {
                            duplicate = true;
                            break;
                        }
                    }

                    if(!duplicate && !UNASSIGNED.equals(name))
                    {
                        spans.add(new TunerSpan(name, center, center - half, center + half));
                    }
                }
            }
        }
        catch(Exception e)
        {
            mLog.error("Auto-start: unable to read tuner spans - channels will start in frequency order only", e);
            spans.clear();
        }

        spans.sort(Comparator.comparingLong(TunerSpan::center));
        return spans;
    }

    /**
     * Determines the tuner group for a channel.
     */
    private static String assign(Channel channel, long frequency, List<TunerSpan> spans)
    {
        String preferred = getPreferredTuner(channel);

        if(preferred != null && !preferred.isBlank())
        {
            for(TunerSpan span : spans)
            {
                if(span.name().equalsIgnoreCase(preferred))
                {
                    return span.name();
                }
            }
        }

        if(frequency > 0)
        {
            TunerSpan best = null;

            for(TunerSpan span : spans)
            {
                if(frequency >= span.minimum() && frequency <= span.maximum())
                {
                    if(best == null || Math.abs(frequency - span.center()) < Math.abs(frequency - best.center()))
                    {
                        best = span;
                    }
                }
            }

            if(best != null)
            {
                return best.name();
            }
        }

        return UNASSIGNED;
    }

    /**
     * Primary frequency for the channel, or 0 if the channel is not tuner-sourced.
     */
    private static long getFrequency(Channel channel)
    {
        SourceConfiguration config = channel.getSourceConfiguration();

        if(config instanceof SourceConfigTuner sct)
        {
            return sct.getFrequency();
        }
        else if(config instanceof SourceConfigTunerMultipleFrequency sctmf)
        {
            List<Long> frequencies = sctmf.getFrequencies();

            if(frequencies != null && !frequencies.isEmpty() && frequencies.getFirst() != null)
            {
                return frequencies.getFirst();
            }
        }

        return 0;
    }

    /**
     * Preferred tuner name for the channel, or null.
     */
    private static String getPreferredTuner(Channel channel)
    {
        SourceConfiguration config = channel.getSourceConfiguration();

        if(config instanceof SourceConfigTuner sct)
        {
            return sct.getPreferredTuner();
        }
        else if(config instanceof SourceConfigTunerMultipleFrequency sctmf)
        {
            return sctmf.getPreferredTuner();
        }

        return null;
    }

    private static void logPlan(String group, List<TunerSpan> spans, List<Entry> entries)
    {
        for(TunerSpan span : spans)
        {
            if(span.name().equals(group))
            {
                mLog.info(String.format(Locale.US, "Auto-start plan: tuner [%s] %.4f-%.4f MHz - %d channels",
                        group, span.minimum() / 1E6, span.maximum() / 1E6, entries.size()));
                return;
            }
        }

        mLog.info("Auto-start plan: [" + group + "] - " + entries.size() + " channels");
    }

    private record TunerSpan(String name, long center, long minimum, long maximum) {}
    private record Entry(Channel channel, long frequency, int index) {}
    private record Batch(String group, boolean firstInGroup, List<Channel> channels) {}
}
