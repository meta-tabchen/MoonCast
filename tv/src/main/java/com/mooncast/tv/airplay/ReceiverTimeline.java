package com.mooncast.tv.airplay;

import java.util.ArrayDeque;

/** Bounded numeric/event-only record. No payload, peer name, URI, address or exception message.
 * Diagnostic rejection must never interrupt receiver teardown or an uncaught-error handler. */
public final class ReceiverTimeline {
    private final ArrayDeque<String> events=new ArrayDeque<>();
    public synchronized void add(long elapsed,String code,long value){
        int invalid=invalidOffset(code);
        if(invalid!=-1){
            // Numeric evidence distinguishes null/length/character rejection without recording input.
            append(elapsed,"diagnostic-rejected-length",code==null?-1:code.length());
            append(elapsed,"diagnostic-rejected-hash",code==null?0:code.hashCode());
            append(elapsed,"diagnostic-rejected-offset",invalid);
            append(elapsed,"diagnostic-rejected-char",code!=null&&invalid>=0&&invalid<code.length()?code.charAt(invalid):-1);
            return;
        }
        append(elapsed,code,value);
    }
    private static int invalidOffset(String code){
        if(code==null||code.isEmpty()||code.length()>40)return -2;
        for(int i=0;i<code.length();i++){char c=code.charAt(i);if(!((c>='a'&&c<='z')||(c>='0'&&c<='9')||c=='_'||c=='-'))return i;}
        return -1;
    }
    private void append(long elapsed,String code,long value){
        events.addLast(Math.max(0,elapsed)+" "+code+" "+value);
        while(events.size()>96)events.removeFirst();
    }
    public synchronized String text(){return String.join("\n",events);}
}
