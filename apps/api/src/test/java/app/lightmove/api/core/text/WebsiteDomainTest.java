package app.lightmove.api.core.text;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.text.service.WebsiteDomain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The registrable domain a client record stores, taken out of whatever Apollo publishes as a website. */
class WebsiteDomainTest {

    @Test
    @DisplayName("the host is kept, and everything around it is not")
    void keepsOnlyTheHost() {
        assertThat(WebsiteDomain.of("https://www.acwapower.com/en")).isEqualTo("acwapower.com");
        assertThat(WebsiteDomain.of("http://ACWAPOWER.com")).isEqualTo("acwapower.com");
        assertThat(WebsiteDomain.of("  https://acwapower.com  ")).isEqualTo("acwapower.com");
    }

    @Test
    @DisplayName("a bare host with no scheme still resolves")
    void handlesASchemelessHost() {
        assertThat(WebsiteDomain.of("acwapower.com")).isEqualTo("acwapower.com");
        assertThat(WebsiteDomain.of("www.acwapower.com/careers")).isEqualTo("acwapower.com");
    }

    @Test
    @DisplayName("a port and a userinfo are not part of the domain")
    void dropsPortAndUserinfo() {
        // Both survived the regex this replaced, and a domain column holding "acwapower.com:8080" is
        // one nothing can match against.
        assertThat(WebsiteDomain.of("https://acwapower.com:8080/")).isEqualTo("acwapower.com");
        assertThat(WebsiteDomain.of("https://user@acwapower.com")).isEqualTo("acwapower.com");
    }

    @Test
    @DisplayName("anything that is not a domain is dropped rather than stored raw")
    void dropsWhatItCannotParse() {
        assertThat(WebsiteDomain.of(null)).isNull();
        assertThat(WebsiteDomain.of("   ")).isNull();
        assertThat(WebsiteDomain.of("localhost")).isNull();
        assertThat(WebsiteDomain.of("https://")).isNull();
        assertThat(WebsiteDomain.of("http:// not a url")).isNull();
    }
}
