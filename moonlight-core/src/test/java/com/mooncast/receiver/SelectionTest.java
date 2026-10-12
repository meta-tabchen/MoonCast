package com.mooncast.receiver;

import com.limelight.nvstream.http.NvApp;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class SelectionTest {
    @Test public void usesActualDesktopId() {
        NvApp desktop = new NvApp("Desktop", 42, false);
        assertSame(desktop, DesktopSelection.requireDesktop(Arrays.asList(
                new NvApp("Steam", 1, false), desktop)));
    }
    @Test public void acceptsCaseOnlyVariation() {
        assertEquals(9, DesktopSelection.requireDesktop(Collections.singletonList(
                new NvApp("desktop", 9, false))).getAppId());
    }
    @Test(expected = IllegalArgumentException.class) public void refusesUnrelatedApp() {
        DesktopSelection.requireDesktop(Collections.singletonList(new NvApp("Steam", 1, false)));
    }
    @Test(expected = IllegalArgumentException.class) public void refusesMissingId() {
        DesktopSelection.requireDesktop(Collections.singletonList(new NvApp("Desktop")));
    }
    @Test(expected = IllegalArgumentException.class) public void refusesDuplicateDesktop() {
        DesktopSelection.requireDesktop(Arrays.asList(new NvApp("Desktop", 1, false), new NvApp("Desktop", 2, false)));
    }
    @Test public void acceptsFourDigitPinWithLeadingZero() { DesktopSelection.validatePin("0042"); }
    @Test(expected = IllegalArgumentException.class) public void refusesNonAsciiPin() { DesktopSelection.validatePin("１２３４"); }
    @Test(expected = IllegalArgumentException.class) public void refusesLongPin() { DesktopSelection.validatePin("12345"); }
    @Test public void explicitBitrateUnits() { assertEquals(30000, StreamSpec.conservative1080p().bitrateKbps); }
    @Test(expected = IllegalArgumentException.class) public void refusesOddDimensions() { new StreamSpec(1919,1080,60,20000); }
    @Test(expected = IllegalArgumentException.class) public void refusesUnsupportedFrameRate() { new StreamSpec(1920,1080,120,20000); }
    @Test(expected = IllegalArgumentException.class) public void refusesBitrateInWrongUnits() { new StreamSpec(1920,1080,60,20000000); }
}
