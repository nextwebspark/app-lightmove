package app.lightmove.api.core.platformconfig.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.platformconfig.constant.PlatformPrompt;
import app.lightmove.api.core.platformconfig.constant.PlatformSetting;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlatformOverridesTest {

    private final FakeStore store = new FakeStore();
    private final MovableClock clock = new MovableClock(Instant.parse("2026-11-02T09:00:00Z"));
    private final PlatformOverrides overrides = new PlatformOverrides(store, clock, Duration.ofSeconds(30));

    @Test
    @DisplayName("the first read loads, and typed values and active prompts land in the snapshot")
    void firstReadLoads() {
        store.settings.put("enrichment.sourcing.picks-per-company", "4");
        store.prompts.put("outreach-opener", "Write one line.");

        var snapshot = overrides.current();

        assertThat(snapshot.settings()).containsEntry(PlatformSetting.SOURCING_PICKS_PER_COMPANY, 4L);
        assertThat(snapshot.prompts()).containsEntry(PlatformPrompt.OUTREACH_OPENER, "Write one line.");
    }

    @Test
    @DisplayName("within the interval nothing is asked; past it the version is, and only a moved one reloads")
    void intervalGatesTheVersionCheck() {
        overrides.current();
        int checks = store.versionReads;

        store.settings.put("mcp.max-result-chars", "20000");
        store.version = 2;
        clock.advance(Duration.ofSeconds(10));
        assertThat(overrides.current().settings()).doesNotContainKey(PlatformSetting.MCP_MAX_RESULT_CHARS);
        assertThat(store.versionReads).isEqualTo(checks);

        clock.advance(Duration.ofSeconds(25));
        assertThat(overrides.current().settings()).containsEntry(PlatformSetting.MCP_MAX_RESULT_CHARS, 20_000L);
        assertThat(overrides.current().version()).isEqualTo(2);
    }

    @Test
    @DisplayName("a NOTIFY reloads on the next read without waiting for the interval")
    void markStaleReloadsAtOnce() {
        overrides.current();
        store.prompts.put("call-changes", "Changed.");

        overrides.markStale();

        assertThat(overrides.current().prompts()).containsEntry(PlatformPrompt.CALL_CHANGES, "Changed.");
    }

    @Test
    @DisplayName("a key this build does not allow, or a value its bounds refuse, is skipped and runs on the default")
    void unknownOrRefusedRowsAreSkipped() {
        store.settings.put("auth.jwt.private-key", "anything");
        store.settings.put("enrichment.sourcing.picks-per-company", "99");
        store.prompts.put("a-prompt-from-a-later-build", "Hello.");

        var snapshot = overrides.current();

        assertThat(snapshot.settings()).isEmpty();
        assertThat(snapshot.prompts()).isEmpty();
    }

    @Test
    @DisplayName("a check that fails keeps the snapshot it had and tries again on the next read")
    void failedCheckKeepsTheLastSnapshot() {
        store.settings.put("export.max-companies", "100");
        overrides.current();

        store.failing = true;
        overrides.markStale();
        assertThat(overrides.current().settings()).containsEntry(PlatformSetting.EXPORT_MAX_COMPANIES, 100L);

        store.failing = false;
        store.settings.put("export.max-companies", "200");
        assertThat(overrides.current().settings()).containsEntry(PlatformSetting.EXPORT_MAX_COMPANIES, 200L);
    }

    private static final class FakeStore extends PlatformConfigStore {

        final Map<String, String> settings = new HashMap<>();
        final Map<String, String> prompts = new HashMap<>();
        long version = 1;
        int versionReads;
        boolean failing;

        FakeStore() {
            super(null);
        }

        @Override
        public long currentVersion() {
            if (failing) {
                throw new IllegalStateException("database unreachable");
            }
            versionReads++;
            return version;
        }

        @Override
        public Map<String, String> settingValues() {
            return Map.copyOf(settings);
        }

        @Override
        public Map<String, String> activePromptBodies() {
            return Map.copyOf(prompts);
        }
    }

    private static final class MovableClock extends Clock {

        private Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
