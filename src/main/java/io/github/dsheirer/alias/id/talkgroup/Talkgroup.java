/*
 * ******************************************************************************
 * sdrtrunk
 * Copyright (C) 2014-2019 Dennis Sheirer
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
 * *****************************************************************************
 */
package io.github.dsheirer.alias.id.talkgroup;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import io.github.dsheirer.alias.id.AliasID;
import io.github.dsheirer.alias.id.AliasIDType;
import io.github.dsheirer.protocol.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integer talkgroup identifier with protocol.
 */
public class Talkgroup extends AliasID implements Comparable<Talkgroup>
{
    private static final Logger mLog = LoggerFactory.getLogger(Talkgroup.class);
    private Protocol mProtocol = Protocol.UNKNOWN;
    private int mValue;
    private int mTimeslot = ANY_TIMESLOT;

    /**
     * Timeslot value for a talkgroup alias that matches the talkgroup on any timeslot.
     */
    public static final int ANY_TIMESLOT = 0;

    public Talkgroup()
    {
        //No arg JAXB constructor
    }

    public Talkgroup(Protocol protocol, int value)
    {
        mProtocol = protocol;
        mValue = value;
    }

    @Override
    public boolean isAudioIdentifier()
    {
        return false;
    }

    @JacksonXmlProperty(isAttribute = true, localName = "value")
    public int getValue()
    {
        return mValue;
    }

    public void setValue(int value)
    {
        mValue = value;
        updateValueProperty();
    }

    /**
     * Timeslot that this talkgroup alias is limited to (DMR timeslot 1 or 2), or ANY_TIMESLOT (0) to match the
     * talkgroup on any timeslot.  Lets the same talkgroup used on both timeslots of a conventional DMR repeater be
     * aliased (and streamed, with a Stream As Talkgroup) separately for each timeslot.  On trunked DMR systems the
     * timeslot is assigned per call, so these aliases should use any timeslot.
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JacksonXmlProperty(isAttribute = true, localName = "timeslot")
    public int getTimeslot()
    {
        return mTimeslot;
    }

    /**
     * Sets the timeslot that this talkgroup alias is limited to, or ANY_TIMESLOT (0) for any timeslot.
     */
    public void setTimeslot(int timeslot)
    {
        mTimeslot = Math.max(timeslot, ANY_TIMESLOT);
        updateValueProperty();
    }

    @JacksonXmlProperty(isAttribute = true, localName = "protocol")
    public Protocol getProtocol()
    {
        return mProtocol;
    }

    public void setProtocol(Protocol protocol)
    {
        mProtocol = protocol;
        updateValueProperty();
    }

    @Override
    public boolean isValid()
    {
        if(mProtocol == null || mProtocol == Protocol.UNKNOWN)
        {
            return false;
        }

        TalkgroupFormat talkgroupFormat = TalkgroupFormat.get(mProtocol);
        return talkgroupFormat.getMinimumValidValue() <= mValue && mValue <= talkgroupFormat.getMaximumValidValue();
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        String talkgroup = null;

        try
        {
            talkgroup = TalkgroupFormatter.format(mProtocol, mValue);
        }
        catch(Exception e)
        {
            mLog.error("Error formatting Talkgroup Protocol [" + mProtocol + "] value [" + mValue + "]", e);
        }

        sb.append("Talkgroup:").append(talkgroup != null ? talkgroup : "error");
        sb.append(" Protocol:").append((mProtocol));

        if(mTimeslot != ANY_TIMESLOT)
        {
            sb.append(" Slot:").append(mTimeslot);
        }

        if(!isValid())
        {
            sb.append(" **NOT VALID**");
        }

        return sb.toString();
    }

    @Override
    public boolean matches(AliasID id)
    {
        if(id instanceof Talkgroup)
        {
            Talkgroup tgid = (Talkgroup)id;
            return (getProtocol() == tgid.getProtocol()) && (getValue() == tgid.getValue()) &&
                (getTimeslot() == tgid.getTimeslot());
        }

        return false;
    }

    @JacksonXmlProperty(isAttribute = true, localName = "type", namespace = "http://www.w3.org/2001/XMLSchema-instance")
    @Override
    public AliasIDType getType()
    {
        return AliasIDType.TALKGROUP;
    }

    @Override
    public int compareTo(Talkgroup other)
    {
        if(other == null)
        {
            return -1;
        }

        if(getProtocol().equals(other.getProtocol()))
        {
            int comparison = Integer.compare(getValue(), other.getValue());
            return comparison != 0 ? comparison : Integer.compare(getTimeslot(), other.getTimeslot());
        }
        else
        {
            return getProtocol().compareTo(other.getProtocol());
        }
    }
}
