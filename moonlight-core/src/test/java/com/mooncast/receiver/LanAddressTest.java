package com.mooncast.receiver;
import org.junit.Test;
import static org.junit.Assert.*;
public class LanAddressTest {
    @Test public void acceptsPrivateV4() { assertEquals("192.168.1.2", LanAddress.require("192.168.1.2",47989).address); }
    @Test public void accepts10And172() { LanAddress.require("10.0.0.1",47989); LanAddress.require("172.31.255.1",47989); }
    @Test public void acceptsLinkLocalV4() { LanAddress.require("169.254.42.1",47989); }
    @Test public void acceptsIpv6Ula() { LanAddress.require("[fd12::42]",47989); }
    @Test(expected=IllegalArgumentException.class) public void rejectsPublic() { LanAddress.require("8.8.8.8",47989); }
    @Test(expected=IllegalArgumentException.class) public void rejectsPublicIpv6() { LanAddress.require("2001:4860:4860::8888",47989); }
    @Test(expected=IllegalArgumentException.class) public void rejectsLoopback() { LanAddress.require("127.0.0.1",47989); }
    @Test(expected=IllegalArgumentException.class) public void rejectsLoopbackIpv6() { LanAddress.require("::1",47989); }
    @Test(expected=IllegalArgumentException.class) public void rejectsHostname() { LanAddress.require("phone.example.com",47989); }
    @Test(expected=IllegalArgumentException.class) public void rejectsMulticast() { LanAddress.require("224.0.0.251",47989); }
    @Test(expected=IllegalArgumentException.class) public void rejectsOctalAmbiguity() { LanAddress.require("010.0.0.1",47989); }
    @Test(expected=IllegalArgumentException.class) public void rejectsOversizePort() { LanAddress.require("10.0.0.1",65536); }
}
