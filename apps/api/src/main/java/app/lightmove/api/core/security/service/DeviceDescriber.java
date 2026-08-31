package app.lightmove.api.core.security.service;

import app.lightmove.api.core.security.constant.DeviceKind;
import app.lightmove.api.core.security.model.DeviceDescription;
import app.lightmove.api.core.security.token.SessionClient;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Turns a session's client and User-Agent into the line Settings → Active sessions shows.
 *
 * <p>A best-effort label, never a security decision — a User-Agent is client-supplied and freely
 * forged. It exists so someone can recognise their own laptop in a list, and an unrecognised string
 * is reported as unknown rather than guessed at.
 */
@Component
public class DeviceDescriber {

    /**
     * Order is the whole algorithm. Browsers quote each other's names for compatibility: Edge's
     * User-Agent contains "Chrome" and "Safari", and Chrome's contains "Safari", so the most specific
     * marker has to win. The iOS browsers are checked before Safari for the same reason.
     */
    private static final List<BrowserMarker> BROWSERS = List.of(
            new BrowserMarker("Edg", "Edge"),
            new BrowserMarker("OPR", "Opera"),
            new BrowserMarker("Opera", "Opera"),
            new BrowserMarker("SamsungBrowser", "Samsung Internet"),
            new BrowserMarker("CriOS", "Chrome"),
            new BrowserMarker("FxiOS", "Firefox"),
            new BrowserMarker("Firefox", "Firefox"),
            new BrowserMarker("Chrome", "Chrome"),
            new BrowserMarker("Safari", "Safari"));

    /** The extension's fetches carry the host browser's User-Agent, so only the client tells them apart. */
    private static final String EXTENSION_LABEL = "LightMove Capture (browser extension)";

    private static final List<PlatformMarker> PLATFORMS = List.of(
            new PlatformMarker("iPhone", "iPhone", DeviceKind.MOBILE),
            new PlatformMarker("iPad", "iPad", DeviceKind.TABLET),
            new PlatformMarker("Macintosh", "macOS", DeviceKind.DESKTOP),
            new PlatformMarker("Windows", "Windows", DeviceKind.DESKTOP),
            new PlatformMarker("CrOS", "ChromeOS", DeviceKind.DESKTOP),
            new PlatformMarker("Linux", "Linux", DeviceKind.DESKTOP));

    public DeviceDescription describe(SessionClient client, String userAgent) {
        if (client == SessionClient.BROWSER_EXTENSION) {
            return new DeviceDescription(DeviceKind.EXTENSION, EXTENSION_LABEL);
        }

        if (userAgent == null || userAgent.isBlank()) {
            return DeviceDescription.unknown();
        }

        PlatformMarker platform = platformOf(userAgent);
        String browser = browserOf(userAgent);

        if (platform == null) {
            return browser == null
                    ? DeviceDescription.unknown()
                    : new DeviceDescription(DeviceKind.UNKNOWN, browser);
        }

        String label = browser == null ? platform.name() : "%s — %s".formatted(platform.name(), browser);
        return new DeviceDescription(platform.kind(), label);
    }

    private static PlatformMarker platformOf(String userAgent) {
        // Android before the table: the same platform is a phone or a tablet depending on one token,
        // and it also carries "Linux", which the table would otherwise match first.
        if (userAgent.contains("Android")) {
            return new PlatformMarker("Android", "Android",
                    userAgent.contains("Mobile") ? DeviceKind.MOBILE : DeviceKind.TABLET);
        }
        return PLATFORMS.stream()
                .filter(marker -> userAgent.contains(marker.token()))
                .findFirst()
                .orElse(null);
    }

    private static String browserOf(String userAgent) {
        return BROWSERS.stream()
                .filter(marker -> userAgent.contains(marker.token()))
                .map(BrowserMarker::name)
                .findFirst()
                .orElse(null);
    }

    private record BrowserMarker(String token, String name) {}

    private record PlatformMarker(String token, String name, DeviceKind kind) {}
}
