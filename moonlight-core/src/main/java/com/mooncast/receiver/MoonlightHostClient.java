// SPDX-License-Identifier: GPL-3.0-or-later
package com.mooncast.receiver;

import android.content.Context;
import com.limelight.nvstream.ConnectionContext;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.StreamConfiguration;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.LimelightCryptoProvider;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.jni.MoonBridge;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Objects;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Real GameStream APIs independent of Moonlight UI/database. Blocking operations
 * require a worker. Identity storage belongs to the authorized application owner.
 * This class never creates or writes identities, private keys, PINs or host pins.
 */
public final class MoonlightHostClient implements AutoCloseable {
    private final Context context;
    private final LimelightCryptoProvider identity;
    private final String clientId;
    private final Object requestLock = new Object();
    private NvHTTP pendingRequest;
    private boolean constructingRequest;
    private boolean closed;
    private long generation;

    public MoonlightHostClient(Context context, LimelightCryptoProvider identity, String clientId) {
        this.context = context.getApplicationContext();
        this.identity = Objects.requireNonNull(identity);
        this.clientId = Objects.requireNonNull(clientId);
    }

    public ComputerDetails inspect(ComputerDetails.AddressTuple address, int httpsPort,
            X509Certificate pinnedHostCertificate) throws IOException, XmlPullParserException {
        NvHTTP client = begin(address, httpsPort, pinnedHostCertificate);
        try {
            ComputerDetails result = client.getComputerDetails(true);
            ensureCurrent(client);
            return result;
        } finally { end(client); }
    }

    /** Changes host pairing. Invoke only from a user-initiated PIN pairing flow. */
    public X509Certificate pair(ComputerDetails.AddressTuple address, int httpsPort, String pin)
            throws IOException, XmlPullParserException {
        DesktopSelection.validatePin(pin);
        NvHTTP client = begin(address, httpsPort, null);
        try {
            PairingManager pairing = client.getPairingManager();
            PairingManager.PairState state = pairing.pair(client.getServerInfo(true), pin);
            ensureCurrent(client);
            if (state != PairingManager.PairState.PAIRED || pairing.getPairedCert() == null) {
                throw new IOException("Host pairing did not complete: " + state);
            }
            return pairing.getPairedCert();
        } finally { end(client); }
    }

    /** Authenticated Desktop lookup only. Start explicitly on the returned one-shot handle. */
    public PreparedDesktop prepareDesktop(ComputerDetails.AddressTuple address, int httpsPort,
            X509Certificate pinnedHostCertificate, StreamSpec spec)
            throws IOException, XmlPullParserException {
        Objects.requireNonNull(pinnedHostCertificate, "A paired host certificate is required");
        Objects.requireNonNull(spec);
        address = LanAddress.require(address);
        NvHTTP client = begin(address, httpsPort, pinnedHostCertificate);
        try {
            ComputerDetails details = client.getComputerDetails(true);
            if (details.pairState != PairingManager.PairState.PAIRED) {
                throw new IOException("Host does not recognize this paired identity");
            }
            NvApp desktop;
            try { desktop = DesktopSelection.requireDesktop(client.getAppList()); }
            catch (IllegalArgumentException e) { throw new IOException(e.getMessage(), e); }
            if (details.runningGameId != 0 && details.runningGameId != desktop.getAppId()) {
                throw new IOException("Host has another app running; refusing to replace it");
            }
            StreamConfiguration config = new StreamConfiguration.Builder()
                    .setApp(desktop).setResolution(spec.width, spec.height)
                    .setRefreshRate(spec.framesPerSecond).setLaunchRefreshRate(spec.framesPerSecond)
                    .setClientRefreshRateX100(spec.framesPerSecond * 100).setBitrate(spec.bitrateKbps)
                    .setSupportedVideoFormats(MoonBridge.VIDEO_FORMAT_H264)
                    .setAudioConfiguration(MoonBridge.AUDIO_CONFIGURATION_STEREO)
                    .setAttachedGamepadMask(0).setPersistGamepadsAfterDisconnect(false)
                    .setEnableSops(false).enableAdaptiveResolution(false)
                    // The receiver never requests host-side speaker muting.
                    .enableLocalAudioPlayback(true)
                    .setRemoteConfiguration(StreamConfiguration.STREAM_CFG_LOCAL)
                    .setMaxPacketSize(1392).setColorSpace(MoonBridge.COLORSPACE_REC_709)
                    .setColorRange(MoonBridge.COLOR_RANGE_LIMITED).build();
            NvConnection connection = new NvConnection(context, address, details.httpsPort,
                    clientId, config, identity, pinnedHostCertificate) {
                @Override
                protected boolean quitAndLaunch(NvHTTP host, ConnectionContext connection) {
                    // Host may change apps between lookup and launch. Never replace it.
                    connection.connListener.displayMessage("Host has another app running; refusing to replace it");
                    return false;
                }
            };
            synchronized (requestLock) {
                ensureCurrent(client);
                long preparedGeneration = generation;
                return new PreparedDesktop(connection, spec, desktop.getAppId(), start -> {
                    synchronized (requestLock) {
                        if (closed || generation != preparedGeneration) {
                            throw new IllegalStateException("Prepared host operation was cancelled");
                        }
                        start.run();
                    }
                });
            }
        } finally { end(client); }
    }

    /** Cancels in-flight work and invalidates prepared-but-unstarted handles. Reusable afterward. */
    public void cancelPendingRequests() {
        synchronized (requestLock) {
            generation++;
            if (pendingRequest != null) pendingRequest.cancelRequests();
        }
    }

    /** Permanently disallows future work. Active ReceiverSession has its own stopAsync(). */
    @Override public void close() {
        synchronized (requestLock) {
            closed = true;
            cancelPendingRequests();
        }
    }

    private NvHTTP begin(ComputerDetails.AddressTuple address, int httpsPort,
            X509Certificate certificate) throws IOException {
        long requestGeneration;
        synchronized (requestLock) {
            if (closed) throw new IOException("Host client is closed");
            if (pendingRequest != null || constructingRequest) throw new IllegalStateException("A host operation is already pending");
            constructingRequest = true;
            requestGeneration = generation;
        }
        try {
            address = LanAddress.require(address);
            if (httpsPort < 0 || httpsPort > 65535) throw new IllegalArgumentException("Invalid HTTPS port");
            NvHTTP client = new NvHTTP(address, httpsPort, clientId, certificate, identity);
            synchronized (requestLock) {
                if (closed || generation != requestGeneration) {
                    client.cancelRequests();
                    throw new IOException("Host operation cancelled");
                }
                pendingRequest = client;
                return client;
            }
        } finally {
            synchronized (requestLock) { constructingRequest = false; }
        }
    }

    private void ensureCurrent(NvHTTP client) throws IOException {
        synchronized (requestLock) {
            if (closed || pendingRequest != client || client.isCancelled()) throw new IOException("Host operation cancelled");
        }
    }
    private void end(NvHTTP client) {
        synchronized (requestLock) { if (pendingRequest == client) pendingRequest = null; }
    }
}
