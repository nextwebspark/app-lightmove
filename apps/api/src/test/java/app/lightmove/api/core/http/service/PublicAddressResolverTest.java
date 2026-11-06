package app.lightmove.api.core.http.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The address guard itself; the fetchers' tests prove it refuses a live server on this machine. */
class PublicAddressResolverTest {

    @Test
    @DisplayName("public addresses pass the guard")
    void publicAddressesPass() throws Exception {
        for (String address : new String[] {"8.8.8.8", "1.1.1.1", "2606:4700:4700::1111", "64:ff9b::808:808"}) {
            assertThat(PublicAddressResolver.isPublic(InetAddress.getByName(address))).as(address).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "::1", "10.0.0.1", "169.254.169.254", "192.168.1.10", "172.16.0.1",
            "100.64.0.1", "fc00::1", "fe80::1", "64:ff9b::7f00:1", "0.0.0.0"})
    @DisplayName("an address on this machine or a private network does not")
    void privateAddressesRefused(String address) throws Exception {
        assertThat(PublicAddressResolver.isPublic(InetAddress.getByName(address))).as(address).isFalse();
    }
}
