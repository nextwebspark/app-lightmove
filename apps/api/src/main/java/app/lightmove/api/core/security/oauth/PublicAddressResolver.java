package app.lightmove.api.core.security.oauth;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;

/**
 * Refuses a host when any of its addresses is not public. The connection dials exactly the addresses checked here, so a
 * DNS answer that changes a second later cannot slip a private one past.
 */
public class PublicAddressResolver implements DnsResolver {

    private final DnsResolver system = SystemDefaultDnsResolver.INSTANCE;

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] addresses = system.resolve(host);
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw new NonPublicAddressException(host);
            }
        }
        return addresses;
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        return system.resolveCanonicalHostname(host);
    }

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            return first != 0                                        // this network
                    && !(first == 100 && (second & 0xC0) == 64)      // carrier-grade NAT, 100.64/10
                    && !(first == 192 && second == 0 && (bytes[2] & 0xFF) == 0) // IETF protocol assignments
                    && !(first == 198 && (second & 0xFE) == 18)      // benchmarking, 198.18/15
                    && first < 240;                                  // reserved and broadcast
        }
        if (address instanceof Inet6Address) {
            if ((bytes[0] & 0xFE) == 0xFC) {                         // unique-local, fc00::/7
                return false;
            }
            if (isNat64(bytes)) {                                    // 64:ff9b::/96 carries an IPv4 address
                try {
                    return isPublic(InetAddress.getByAddress(new byte[] {bytes[12], bytes[13], bytes[14], bytes[15]}));
                } catch (UnknownHostException impossible) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isNat64(byte[] bytes) {
        if (bytes[0] != 0 || bytes[1] != 0x64 || (bytes[2] & 0xFF) != 0xFF || (bytes[3] & 0xFF) != 0x9B) {
            return false;
        }
        for (int i = 4; i < 12; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    /** Thrown for a host that resolves somewhere it must not, so the fetcher can say why. */
    static final class NonPublicAddressException extends UnknownHostException {

        NonPublicAddressException(String host) {
            super(host + " resolves to an address that is not public");
        }
    }
}
