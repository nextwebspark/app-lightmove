package app.lightmove.api.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.core.config.EnrichmentSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.RetentionSettings;
import app.lightmove.api.core.tenant.TenantResolvers;
import app.lightmove.api.enrichment.candidate.service.CachedPeopleStore;
import app.lightmove.api.retention.constant.RetentionTarget;
import app.lightmove.api.retention.service.RetentionSweeper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class RetentionSweeperTest {

    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");
    private static final UUID FIRST = UUID.randomUUID();
    private static final UUID SECOND = UUID.randomUUID();

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final TenantResolvers resolvers = mock(TenantResolvers.class);
    private final CachedPeopleStore people = mock(CachedPeopleStore.class);

    @Test
    @DisplayName("a table failing, globally or in one workspace, holds back no other table and no other workspace")
    void oneFailureLeavesTheRestSwept() {
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenAnswer(call -> {
            String sql = call.getArgument(0);
            MapSqlParameterSource parameters = call.getArgument(1);
            boolean invitationsOfFirst = sql.contains("app_lm_invitation")
                    && FIRST.equals(parameters.getValue("workspaceId"));
            if (sql.contains("app_lm_verification_token") || invitationsOfFirst) {
                throw new QueryTimeoutException("lock timeout");
            }
            return 0;
        });
        when(people.purgeFetchedBefore(any())).thenReturn(3);
        when(resolvers.workspacesWithRetentionDue(any(), any(), any(), any(), any()))
                .thenReturn(List.of(FIRST, SECOND));

        var deleted = sweeper().sweepAt(NOW);

        assertThat(deleted).doesNotContainKey(RetentionTarget.VERIFICATION_TOKENS)
                .containsEntry(RetentionTarget.MFA_CHALLENGES, 0)
                .containsEntry(RetentionTarget.VENDOR_PEOPLE, 3)
                .containsEntry(RetentionTarget.INVITATIONS, 0)
                .containsEntry(RetentionTarget.USAGE_EVENTS, 0);
        verify(jdbc).update(argThat(sql -> sql.contains("app_lm_usage_event")),
                argThat((MapSqlParameterSource parameters) -> FIRST.equals(parameters.getValue("workspaceId"))));
        verify(jdbc).update(argThat(sql -> sql.contains("app_lm_invitation")),
                argThat((MapSqlParameterSource parameters) -> SECOND.equals(parameters.getValue("workspaceId"))));
    }

    @Test
    @DisplayName("a resolver that fails still leaves the global tables swept")
    void resolverFailureLeavesGlobalTablesSwept() {
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);
        when(resolvers.workspacesWithRetentionDue(any(), any(), any(), any(), any()))
                .thenThrow(new QueryTimeoutException("statement timeout"));

        var deleted = sweeper().sweepAt(NOW);

        assertThat(deleted).containsEntry(RetentionTarget.REFRESH_TOKENS, 0)
                .doesNotContainKey(RetentionTarget.INVITATIONS);
    }

    private RetentionSweeper sweeper() {
        LightMoveProperties properties = mock(LightMoveProperties.class);
        EnrichmentSettings enrichment = mock(EnrichmentSettings.class);
        when(enrichment.companyCacheTtl()).thenReturn(Duration.ofDays(30));
        when(enrichment.peopleCacheTtl()).thenReturn(Duration.ofDays(30));
        when(properties.enrichment()).thenReturn(enrichment);
        when(properties.retention()).thenReturn(new RetentionSettings("PT1H", Duration.ofDays(30), Duration.ofDays(30),
                Duration.ofDays(1), Duration.ofDays(90), Duration.ofDays(90), Duration.ofDays(90), Duration.ofDays(180),
                Duration.ofDays(400)));
        return new RetentionSweeper(jdbc, resolvers, people, Clock.systemUTC(), properties);
    }
}
