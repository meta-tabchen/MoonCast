// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.tv.gamestream.hosts;
import java.security.cert.X509Certificate;
/** The paired boolean is only a synthetic authorization input, never a certificate. */
public final class HostRecord {
    public final HostEndpoint endpoint;
    public final int httpsPort;
    public final X509Certificate pinnedCertificate;
    public boolean paired = true;
    public HostRecord(HostEndpoint endpoint, String name, int port, String uuid, X509Certificate certificate) {
        this.endpoint = endpoint; httpsPort = port; pinnedCertificate = certificate;
    }
    public boolean isPaired() { return paired; }
}
