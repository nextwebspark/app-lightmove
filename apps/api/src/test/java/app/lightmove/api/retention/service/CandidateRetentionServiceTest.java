package app.lightmove.api.retention.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.candidate.service.PersonErasureService;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.RetentionSettings;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class CandidateRetentionServiceTest {

    private final LightMoveProperties properties = mock(LightMoveProperties.class);

    @Test
    @DisplayName("the never-contacted period is the pass's own, and null wherever no pass erases anyone")
    void neverContactedPeriodIsThePassInForce() {
        when(properties.retention()).thenReturn(mock(RetentionSettings.class));
        NeverContactedErasure running = mock(NeverContactedErasure.class);
        when(running.months()).thenReturn(9);
        NeverContactedErasure blank = mock(NeverContactedErasure.class);
        when(blank.months()).thenReturn(null);

        assertThat(service(Optional.of(running)).period(UUID.randomUUID()).neverContactedMonths()).isEqualTo(9);
        assertThat(service(Optional.of(blank)).period(UUID.randomUUID()).neverContactedMonths()).isNull();
        assertThat(service(Optional.empty()).period(UUID.randomUUID()).neverContactedMonths()).isNull();
    }

    private CandidateRetentionService service(Optional<NeverContactedErasure> neverContacted) {
        return new CandidateRetentionService(mock(NamedParameterJdbcTemplate.class), mock(PersonErasureService.class),
                mock(AuditService.class), Clock.systemUTC(), properties, neverContacted);
    }
}
