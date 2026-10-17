package com.mooncast.host;
public final class ScreenDimStateTest {
 public static void main(String[] args){ScreenDimState s=new ScreenDimState();check(!s.active());int first=s.request();check(s.active());s.beginWake();check(!s.activate(first));check(s.active());s.bright();check(!s.active());int second=s.request();check(s.activate(second));check(s.phase==ScreenDimState.Phase.DIMMED);s.beginWake();check(s.phase==ScreenDimState.Phase.WAKING);check(!s.activate(second));s.bright();check(!s.active());int old=s.request();int fresh=s.request();check(!s.activate(old)&&s.activate(fresh));s.bright();check(!s.active());System.out.println("PASS window dimming state: countdown cancellation, stale tasks, complete waking gesture; no physical-brightness claim");}
 static void check(boolean b){if(!b)throw new AssertionError();}
}
