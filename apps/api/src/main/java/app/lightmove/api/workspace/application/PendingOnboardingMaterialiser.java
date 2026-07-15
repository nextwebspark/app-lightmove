package app.lightmove.api.workspace.application;

import app.lightmove.api.auth.domain.EmailVerifiedEvent;
import app.lightmove.api.common.security.AuthPrincipal;
import app.lightmove.api.invitation.application.InvitationService;
import app.lightmove.api.invitation.application.InviteCommand;
import app.lightmove.api.workspace.domain.PendingOnboarding;
import app.lightmove.api.workspace.domain.Workspace;
import app.lightmove.api.workspace.domain.WorkspaceRole;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns the signup wizard into a real organisation, at the moment the user proves their mailbox.
 *
 * <p>An unverified user can fill in every step of signup — that is their own signup, and stopping them
 * halfway through it with a 403 was a dead end. What they cannot do is cause anything to <i>exist</i>:
 * no workspace on their firm's domain, no request in an admin's queue, no email sent to a colleague on
 * the strength of an address nobody has checked. Their answers are held (see {@link PendingOnboarding}),
 * and this is where they are cashed in.
 *
 * <p>A separate bean, not a method on {@code VerificationService} — and not a method that service calls
 * on itself. Spring's {@code @Transactional} and {@code @EventListener} are proxy-based, so a class that
 * invokes its own annotated method bypasses the proxy and the annotation does nothing at all. This
 * codebase has shipped that bug before; {@code AuditService} delegates to {@code AuditEventWriter} for
 * exactly this reason.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PendingOnboardingMaterialiser {

    private final OnboardingService onboarding;
    private final InvitationService invitations;

    /**
     * Synchronous, and therefore inside the verifying transaction: the workspace and the verification
     * commit together or not at all.
     */
    @EventListener
    public void onEmailVerified(EmailVerifiedEvent event) {
        onboarding.materialise(event.userId(), event.request())
                .ifPresent(materialised -> sendHeldInvitations(event, materialised));
    }

    /**
     * Step 3's invitations, sent now that there is a workspace to invite anyone into.
     *
     * <p>Only reached for a CREATE — a user who asked to <i>join</i> a workspace is not its admin and had
     * nobody to invite.
     */
    private void sendHeldInvitations(EmailVerifiedEvent event, OnboardingService.Materialised materialised) {
        Workspace workspace = materialised.workspace();
        List<PendingOnboarding.PendingInvite> held = materialised.invitations();

        if (workspace == null || held.isEmpty()) {
            return;
        }

        List<InviteCommand> commands = held.stream()
                .map(invite -> new InviteCommand(invite.email(),
                        invite.role() == null ? WorkspaceRole.CONSULTANT : invite.role()))
                .toList();

        // The principal is assembled here rather than taken from the security context, because the token
        // the user is holding was minted before this workspace existed and still says they have none.
        AuthPrincipal principal = new AuthPrincipal(
                event.userId(), null, workspace.getId(), WorkspaceRole.ADMIN, true);

        int sent = invitations.invite(principal, commands, event.request()).size();
        log.info("Sent {} held invitation(s) for newly created workspace {}", sent, workspace.getId());
    }
}
