package app.lightmove.api.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.DataProtectionSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class OutreachPrivacyServiceTest {

    @Test
    @DisplayName("with data protection off an email goes as it did before #924: no footer, no link minted")
    void switchedOffAddsNothing() {
        LightMoveProperties properties = mock(LightMoveProperties.class);
        when(properties.dataProtection()).thenReturn(new DataProtectionSettings(false, 500));
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        PersonRepository people = mock(PersonRepository.class);
        OutreachPrivacyService privacy = new OutreachPrivacyService(properties, jdbc, people,
                mock(CandidatePrivacySettingsService.class), mock(PersonActivityRecorder.class),
                mock(AuditService.class));

        assertThat(privacy.footerFor(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()))
                .isNull();
        assertThat(privacy.footerStatementsOf(UUID.randomUUID(), List.of(UUID.randomUUID()))).isEmpty();
        verifyNoInteractions(jdbc, people);
    }
}
