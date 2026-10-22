package app.lightmove.api.notetaker.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.notetaker.constant.NotetakerEndReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FallbackPolicyTest {

    @Test
    @DisplayName("ours failing to start, getting in or staying in is covered; a person's decision never is")
    void coversOnlyOurOwnFailures() {
        FallbackPolicy policy = new FallbackPolicy(true, false);

        assertThat(policy.fallsBack(NotetakerEndReason.LAUNCH_FAILED)).isTrue();
        assertThat(policy.fallsBack(NotetakerEndReason.LAUNCH_TIMEOUT)).isTrue();
        assertThat(policy.fallsBack(NotetakerEndReason.JOIN_BLOCKED)).isTrue();
        assertThat(policy.fallsBack(NotetakerEndReason.CRASHED)).isTrue();
        assertThat(policy.fallsBack(NotetakerEndReason.HEARTBEAT_LOST)).isTrue();
        assertThat(policy.fallsBack(NotetakerEndReason.NOT_ADMITTED)).isFalse();
        assertThat(policy.fallsBack(NotetakerEndReason.CONSENT_NOT_SHOWN)).isFalse();
        assertThat(policy.fallsBack(NotetakerEndReason.DO_NOT_CONTACT)).isFalse();
        assertThat(policy.fallsBack(NotetakerEndReason.PROCESSING_FAILED)).isFalse();
        assertThat(policy.fallsBack(null)).isFalse();
    }

    @Test
    @DisplayName("a bot nobody admitted is followed only where the deployment says so, and nothing is when it is off")
    void notAdmittedAndOff() {
        assertThat(new FallbackPolicy(true, true).fallsBack(NotetakerEndReason.NOT_ADMITTED)).isTrue();
        assertThat(new FallbackPolicy(false, true).fallsBack(NotetakerEndReason.CRASHED)).isFalse();
    }
}
