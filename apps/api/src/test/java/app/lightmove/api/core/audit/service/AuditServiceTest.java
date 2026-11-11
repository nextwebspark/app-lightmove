package app.lightmove.api.core.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.model.AuditEvent;
import app.lightmove.api.core.security.service.ClientIpResolver;
import java.util.List;
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
    @DisplayName("a reason goes through the same backstop as every other detail")
    void redactsAnAddressInAReason() {
        audit.event(AuthEventType.LOGIN_FAILED).reason("invalid_client for jane.doe@example.com").record();

        assertThat(recorded().getMetadata()).containsEntry("reason", "invalid_client for [email removed]");
    }

    @Test
    @DisplayName("an address inside a list or a map is recorded without it")
    void redactsAnAddressInsideCollections() {
        audit.event(AuthEventType.PROFILE_UPDATED)
                .detail("skipped", List.of("jane.doe@example.com", "3f1c"))
                .detail("counts", Map.of("sent to a@b.example", 2))
                .record();

        assertThat(recorded().getMetadata())
                .containsEntry("skipped", List.of("[email removed]", "3f1c"))
                .containsEntry("counts", Map.of("sent to [email removed]", 2));
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
