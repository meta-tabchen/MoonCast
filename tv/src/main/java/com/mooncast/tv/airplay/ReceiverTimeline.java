package com.mooncast.tv.airplay;

import java.util.ArrayDeque;

/** Bounded numeric/event-only record. No payload, peer name, URI, address or exception message. */
public final class ReceiverTimeline {
    private final ArrayDeque<String> events=new ArrayDeque<>();
    public synchronized void add(long elapsed,String code,long value){
        if(code==null||!code.matches("[a-z0-9_-]{1,40}"))throw new IllegalArgumentException("event code");
        events.addLast(Math.max(0,elapsed)+" "+code+" "+value);
        while(events.size()>96)events.removeFirst();
    }
    public synchronized String text(){return String.join("\n",events);}
}
