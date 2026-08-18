package app.lightmove.api.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.security.constant.DeviceKind;
import app.lightmove.api.core.security.model.DeviceDescription;
import app.lightmove.api.core.security.service.DeviceDescriber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Browsers quote each other's names for compatibility, so the order the markers are tested in <i>is</i>
 * the algorithm. Every case here is a real User-Agent that a naive "contains" would get wrong.
 */
class DeviceDescriberTest {

    private final DeviceDescriber describer = new DeviceDescriber();

    @Test
    @DisplayName("Edge is not Chrome, and Chrome is not Safari")
    void theMostSpecificBrowserMarkerWins() {
        assertThat(describer.describe("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0"))
                .isEqualTo(new DeviceDescription(DeviceKind.DESKTOP, "Windows — Edge"));

        assertThat(describer.describe("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"))
                .isEqualTo(new DeviceDescription(DeviceKind.DESKTOP, "macOS — Chrome"));
    }

    @Test
    @DisplayName("Safari on a Mac is Safari")
    void safariIsRecognisedWhenNothingElseClaimsIt() {
        assertThat(describer.describe("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 "
                + "(KHTML, like Gecko) Version/17.1 Safari/605.1.15"))
                .isEqualTo(new DeviceDescription(DeviceKind.DESKTOP, "macOS — Safari"));
    }

    @Test
    @DisplayName("Chrome on iOS says CriOS and never says Chrome")
    void iosBrowsersAreReadBeforeSafari() {
        assertThat(describer.describe("Mozilla/5.0 (iPhone; CPU iPhone OS 17_1 like Mac OS X) "
                + "AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/119.0.0.0 Mobile/15E148 Safari/604.1"))
                .isEqualTo(new DeviceDescription(DeviceKind.MOBILE, "iPhone — Chrome"));
    }

    @Test
    @DisplayName("an Android phone and an Android tablet differ by one token, and both carry Linux")
    void androidIsSplitByFormFactorAndBeatsLinux() {
        assertThat(describer.describe("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"))
                .isEqualTo(new DeviceDescription(DeviceKind.MOBILE, "Android — Chrome"));

        assertThat(describer.describe("Mozilla/5.0 (Linux; Android 14; SM-X710) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"))
                .isEqualTo(new DeviceDescription(DeviceKind.TABLET, "Android — Chrome"));
    }

    @Test
    @DisplayName("an iPad is a tablet")
    void ipadIsATablet() {
        assertThat(describer.describe("Mozilla/5.0 (iPad; CPU OS 17_1 like Mac OS X) AppleWebKit/605.1.15 "
                + "(KHTML, like Gecko) Version/17.1 Mobile/15E148 Safari/604.1").kind())
                .isEqualTo(DeviceKind.TABLET);
    }

    @Test
    @DisplayName("nothing recognisable is reported as unknown rather than guessed at")
    void unrecognisedInputIsUnknown() {
        assertThat(describer.describe(null)).isEqualTo(DeviceDescription.unknown());
        assertThat(describer.describe("  ")).isEqualTo(DeviceDescription.unknown());
        assertThat(describer.describe("curl/8.4.0")).isEqualTo(DeviceDescription.unknown());
    }
}
