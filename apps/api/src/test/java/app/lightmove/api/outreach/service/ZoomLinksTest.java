package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ZoomLinksTest {

    @Test
    @DisplayName("a Zoom link's meeting number is read from its join, web-client or short path, and nothing else")
    void meetingNumbers() {
        assertThat(ZoomLinks.meetingIdOf("https://us05web.zoom.us/j/81234567890?pwd=abc")).contains("81234567890");
        assertThat(ZoomLinks.meetingIdOf("https://zoom.us/wc/join/812345678")).contains("812345678");
        assertThat(ZoomLinks.meetingIdOf("https://firm.zoom.us/w/81234567890?tk=x")).contains("81234567890");
        assertThat(ZoomLinks.meetingIdOf("https://zoom.us/my/yara.haddad")).isEmpty();
        assertThat(ZoomLinks.meetingIdOf("https://zoom.us/j/1234")).isEmpty();
        assertThat(ZoomLinks.meetingIdOf(null)).isEmpty();
    }
}
