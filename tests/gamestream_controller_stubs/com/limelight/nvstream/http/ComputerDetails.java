// SPDX-License-Identifier: GPL-3.0-or-later
package com.limelight.nvstream.http;
public final class ComputerDetails {
    public int httpsPort = 47984;
    public String uuid = "synthetic-host";
    public static final class AddressTuple {
        public final String address; public final int port;
        public AddressTuple(String address, int port) { this.address = address; this.port = port; }
    }
}
