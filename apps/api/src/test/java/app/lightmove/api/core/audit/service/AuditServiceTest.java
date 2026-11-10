package app.lightmove.api.core.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.model.AuditEvent;
import app.lightmove.api.core.security.service.ClientIpResolver;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class AuditServiceTest {

    private final AuditEventWriter writer = mock(AuditEventWriter.class);
    private final AuditService audit = new AuditService(writer, mock(ClientIpResolver.class));

    @ParameterizedTest
    @ValueSource(strings = {"email", "fullName", "firstName", "lastName", "phone", "linkedinUrl", "profileSlug"})
    @DisplayName("a detail key naming a person's name or contact is refused")
    void refusesPersonalDataKeys(String key) {
        AuditService.Builder event = audit.event(AuthEventType.PROFILE_UPDATED);

        assertThatThrownBy(() -> event.detail(key, "anything")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> event.detailIfPresent(key, "anything")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("an address under an innocent key is recorded without it")
    void redactsAnAddressUnderAnyKey() {
        audit.event(AuthEventType.PROFILE_UPDATED)
                .detail("reason", "bounced for jane.doe@example.com")
                .detail("seats", 3)
                .record();

        assertThat(recorded().getMetadata())
                .containsEntry("reason", "bounced for [email removed]")
                .containsEntry("seats", 3);
    }

    @Test
    @DisplayName("an ordinary detail is recorded as given")
    void recordsOrdinaryDetails() {
        audit.event(AuthEventType.PROFILE_UPDATED).detail("personId", "3f1c").detailIfPresent("left", null).record();

        assertThat(recorded().getMetadata()).containsExactlyEntriesOf(Map.of("personId", "3f1c"));
    }

    private AuditEvent recorded() {
        ArgumentCaptor<AuditEvent> written = ArgumentCaptor.forClass(AuditEvent.class);
        verify(writer).write(written.capture());
        return written.getValue();
    }
}
