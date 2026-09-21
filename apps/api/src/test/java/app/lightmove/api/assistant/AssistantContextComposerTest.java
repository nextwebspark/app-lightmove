package app.lightmove.api.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.assistant.model.AssistantContext;
import app.lightmove.api.assistant.service.AssistantContextComposer;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.project.constant.ProjectStage;
import app.lightmove.api.project.model.ProjectFacts;
import app.lightmove.api.project.service.ProjectService;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a turn is told, derived from the turn's own facts and never from the caller's request.
 */
class AssistantContextComposerTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final UUID MANDATE = UUID.randomUUID();

    private final UserRepository users = mock(UserRepository.class);
    private final ProjectService projects = mock(ProjectService.class);
    private final AssistantContextComposer composer = new AssistantContextComposer(users, projects);

    @Test
    @DisplayName("names the mandate the thread was started about")
    void namesTheThreadsMandate() {
        givenConsultant("Nadia Haddad");
        when(projects.factsOf(WORKSPACE, MANDATE)).thenReturn(Optional.of(
                new ProjectFacts(MANDATE, "Group CFO", "Meridian Energy Group",
                        ProjectStage.MAPPING, LocalDate.of(2026, 11, 30))));

        AssistantContext context = composer.compose(USER, WORKSPACE, MANDATE);

        assertThat(context.consultant()).isEqualTo("Nadia Haddad");
        assertThat(context.hasMandate()).isTrue();
        assertThat(context.mandate().positionTitle()).isEqualTo("Group CFO");
    }

    @Test
    @DisplayName("a thread with no mandate composes without one rather than failing")
    void composesWithoutAMandate() {
        givenConsultant("Nadia Haddad");

        AssistantContext context = composer.compose(USER, WORKSPACE, null);

        assertThat(context.hasMandate()).isFalse();
        verify(projects, never()).factsOf(any(), any());
    }

    @Test
    @DisplayName("a mandate the workspace does not hold simply is not named")
    void leavesAnUnresolvableMandateOut() {
        givenConsultant("Nadia Haddad");
        when(projects.factsOf(WORKSPACE, MANDATE)).thenReturn(Optional.empty());

        AssistantContext context = composer.compose(USER, WORKSPACE, MANDATE);

        // The finder is workspace-scoped, so this is how another firm's mandate arrives: absent.
        // Telling the model about a mandate it could not read would be worse than telling it none.
        assertThat(context.hasMandate()).isFalse();
    }

    @Test
    @DisplayName("a user whose row has gone still yields a usable context")
    void survivesAMissingUser() {
        when(users.findById(USER)).thenReturn(Optional.empty());

        AssistantContext context = composer.compose(USER, WORKSPACE, null);

        assertThat(context.consultant()).isEqualTo("a consultant");
    }

    private void givenConsultant(String fullName) {
        User user = mock(User.class);
        when(user.getFullName()).thenReturn(fullName);
        when(users.findById(USER)).thenReturn(Optional.of(user));
    }
}
