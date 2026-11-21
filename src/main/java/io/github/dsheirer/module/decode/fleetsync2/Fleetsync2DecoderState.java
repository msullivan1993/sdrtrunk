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
package io.github.dsheirer.module.decode.fleetsync2;

import io.github.dsheirer.module.decode.event.ActivitySummary;
import io.github.dsheirer.channel.state.DecoderState;
import io.github.dsheirer.channel.state.DecoderStateEvent;
import io.github.dsheirer.channel.state.State;
import io.github.dsheirer.identifier.Form;
import io.github.dsheirer.identifier.Identifier;
import io.github.dsheirer.identifier.IdentifierClass;
import io.github.dsheirer.identifier.IdentifierCollection;
import io.github.dsheirer.identifier.Role;
import io.github.dsheirer.message.IMessage;
import io.github.dsheirer.module.decode.DecoderType;
import io.github.dsheirer.module.decode.event.DecodeEvent;
import io.github.dsheirer.module.decode.event.DecodeEventType;
import io.github.dsheirer.module.decode.event.PlottableDecodeEvent;
import io.github.dsheirer.module.decode.fleetsync2.identifier.FleetsyncIdentifier;
import io.github.dsheirer.module.decode.fleetsync2.message.Fleetsync2Message;
import io.github.dsheirer.protocol.Protocol;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Fleetsync2DecoderState extends DecoderState
{
    private Map<FleetsyncIdentifier,Integer> mFromIdentCountsMap = new HashMap<>();
    private Map<FleetsyncIdentifier,Integer> mToIdentCountsMap = new HashMap<>();

    public Fleetsync2DecoderState()
    {
        super();
    }

    @Override
    public DecoderType getDecoderType()
    {
        return DecoderType.FLEETSYNC2;
    }

    @Override
    public void init() {}

    @Override
    public void receive(IMessage message)
    {
        if(message.isValid() && message instanceof Fleetsync2Message)
        {
            Fleetsync2Message fleetsync = (Fleetsync2Message)message;

            //Attach the calling unit of a PTT ID (pre or post) or emergency to the current call as the calling radio.
            //The existing identifier is removed first so that a repeat of the same unit is broadcast again: the audio
            //module clears stale calling radio identifiers between calls.  The FROM and TO talkgroup identifiers are
            //not placed in the collection, because the TO talkgroup would replace the channel's talkgroup on the call.
            switch(fleetsync.getMessageType())
            {
                case ANI:
                case EMERGENCY:
                case LONE_WORKER_EMERGENCY:
                    getIdentifierCollection().remove(IdentifierClass.USER, Form.RADIO, Role.FROM);
                    getIdentifierCollection().update(fleetsync.getFromRadioIdentifier());
                    break;
                default:
                    break;
            }

            FleetsyncIdentifier from = fleetsync.getFromIdentifier();

            if(mFromIdentCountsMap.containsKey(from))
            {
                mFromIdentCountsMap.put(from, mFromIdentCountsMap.get(from) + 1);
            }
            else
            {
                mFromIdentCountsMap.put(from, 1);
            }

            FleetsyncIdentifier to = fleetsync.getToIdentifier();

            if(to != null)
            {
                if(mToIdentCountsMap.containsKey(to))
                {
                    mToIdentCountsMap.put(to, mToIdentCountsMap.get(to) + 1);
                }
                else
                {
                    mToIdentCountsMap.put(to, 1);
                }
            }

            switch(fleetsync.getMessageType())
            {
                case ANI:
                case EMERGENCY:
                case LONE_WORKER_EMERGENCY:
                    DecodeEvent aniEvent = getDecodeEvent(fleetsync, getDecodeEventType(fleetsync.getMessageType()));
                    broadcast(aniEvent);
                    broadcast(new DecoderStateEvent(this, DecoderStateEvent.Event.DECODE, State.CALL));
                    break;
                case ACKNOWLEDGE:
                case PAGING:
                case STATUS:
                case UNKNOWN:
                    DecodeEvent statusEvent = getDecodeEvent(fleetsync, getDecodeEventType(fleetsync.getMessageType()));
                    broadcast(statusEvent);
                    broadcast(new DecoderStateEvent(this, DecoderStateEvent.Event.DECODE, State.DATA));
                    break;
                case GPS:
                    PlottableDecodeEvent plottableDecodeEvent = PlottableDecodeEvent.plottableBuilder(DecodeEventType.GPS, fleetsync.getTimestamp())
                        .channel(getCurrentChannel())
                        .details(fleetsync.toString())
                        .identifiers(getEventIdentifiers(fleetsync))
                        .protocol(Protocol.FLEETSYNC)
                        .build();
                    broadcast(plottableDecodeEvent);
                    broadcast(new DecoderStateEvent(this, DecoderStateEvent.Event.DECODE, State.DATA));
                    break;
            }
        }
    }

    /**
     * Identifiers for a decode event: the current identifiers plus the FROM and TO talkgroup identifiers of the
     * message, so that event aliasing and display are unchanged.  The calling radio identifier is left out since the
     * FROM talkgroup identifier already carries the calling unit.
     */
    private IdentifierCollection getEventIdentifiers(Fleetsync2Message fleetsync)
    {
        List<Identifier> identifiers = new ArrayList<>();

        for(Identifier identifier : getIdentifierCollection().getIdentifiers())
        {
            if(!(identifier.getForm() == Form.RADIO && identifier.getRole() == Role.FROM))
            {
                identifiers.add(identifier);
            }
        }

        identifiers.addAll(fleetsync.getIdentifiers());
        return new IdentifierCollection(identifiers);
    }

    private DecodeEvent getDecodeEvent(Fleetsync2Message fleetsync, DecodeEventType eventType) {
        return DecodeEvent.builder(eventType, fleetsync.getTimestamp())
                .channel(getCurrentChannel())
                .details(fleetsync.getMessageType() + " " + fleetsync)
                .identifiers(getEventIdentifiers(fleetsync))
                .protocol(Protocol.FLEETSYNC)
                .build();
    }

    private DecodeEventType getDecodeEventType(FleetsyncMessageType fleetsyncMessageType) {
        switch (fleetsyncMessageType) {
            case ANI:
                return DecodeEventType.ID_ANI;
            case EMERGENCY:
            case LONE_WORKER_EMERGENCY:
                return DecodeEventType.EMERGENCY;
            case ACKNOWLEDGE:
                return DecodeEventType.ACKNOWLEDGE;
            case PAGING:
                return DecodeEventType.PAGE;
            case STATUS:
                return DecodeEventType.STATUS;
            case GPS:
                return DecodeEventType.GPS;
            default:
                return DecodeEventType.UNKNOWN;
        }
    }

    /**
     * Responds to reset events issued by the channel state
     */
    @Override
    public void receiveDecoderStateEvent(DecoderStateEvent event)
    {
        switch(event.getEvent())
        {
            case REQUEST_RESET:
                reset();
                break;
            default:
                break;
        }
    }

    @Override
    public String getActivitySummary()
    {
        ActivitySummary summary = new ActivitySummary("Fleetsync II");
        summary.section("From Idents");
        ActivitySummary.Table from = summary.table("Ident", "Count");
        List<FleetsyncIdentifier> fromIdents = new ArrayList<>(mFromIdentCountsMap.keySet());
        Collections.sort(fromIdents);

        for(FleetsyncIdentifier ident: fromIdents)
        {
            from.row(ident.formatted(), mFromIdentCountsMap.get(ident));
        }

        summary.section("To Idents");
        ActivitySummary.Table to = summary.table("Ident", "Count");
        List<FleetsyncIdentifier> toIdents = new ArrayList<>(mToIdentCountsMap.keySet());
        Collections.sort(toIdents);

        for(FleetsyncIdentifier ident: toIdents)
        {
            to.row(ident.formatted(), mToIdentCountsMap.get(ident));
        }

        return summary.toString();
    }
}
