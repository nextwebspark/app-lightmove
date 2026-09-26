package app.lightmove.api.workspace.service;

import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import app.lightmove.api.core.security.model.AuthenticatedSession;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.security.service.AuthenticationService;
import app.lightmove.api.core.security.token.TokenService;
import app.lightmove.api.core.security.token.Tokens;
import app.lightmove.api.workspace.constant.InvitationStatus;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.model.ClientRepresentativeAcceptedEvent;
import app.lightmove.api.workspace.model.Invitation;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.repository.InvitationRepository;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Redeems invitations — previewing one by its token and every way of accepting it. Reached from the
 * anonymous {@code /onboarding/accept-invitation-signup}, so its checks are imperative, never method security.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationAcceptService {

    private final InvitationRepository invitations;
    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository members;
    private final UserRepository users;
    private final AuditService audit;
    private final AuthenticationService authentication;
    private final TokenService tokens;
    private final RateLimitGuard rateLimit;
    private final ApplicationEventPublisher events;

    /**
     * Unauthenticated, since the invitee usually has no account yet. Discloses only what the email
     * already said, and only to a holder of the 256-bit token mailed to that address.
     */
    @Transactional(readOnly = true)
    public InvitationPreview preview(String plaintextToken) {
        Invitation invitation = resolveRedeemable(plaintextToken, Instant.now());

        Workspace workspace = workspaces.findById(invitation.getWorkspaceId())
                .orElseThrow(() -> ApiException.of(ErrorCode.WORKSPACE_NOT_FOUND));

        String inviterName = users.findById(invitation.getInvitedBy())
                .map(User::getFullName)
                .orElse(null);

        return new InvitationPreview(
                invitation.getEmail(),
                invitation.getRole().getName(),
                workspace.getName(),
                inviterName);
    }

    public record InvitationPreview(String email, String role, String workspaceName,
                                    String inviterName) {}

    /** The invitee lands ACTIVE at once: an admin naming them was the approval. */
    @Transactional
    public WorkspaceMember accept(String plaintextToken, UUID userId, HttpServletRequest request) {
        Instant now = Instant.now();
        Invitation invitation = resolveRedeemable(plaintextToken, now);
        return redeem(invitation, requireUser(userId), now, request);
    }

    /**
     * Token-less: the token only proves the mailbox, which an <b>email-verified</b> user with the
     * matching address already has — e.g. one who verified in a tab without the token.
     */
    @Transactional
    public WorkspaceMember acceptForUser(UUID userId, HttpServletRequest request) {
        Instant now = Instant.now();
        User user = requireUser(userId);

        Invitation invitation = invitations
                .findFirstByEmailAndStatusOrderByCreatedAtDesc(user.getEmail(), InvitationStatus.PENDING)
                .filter(found -> found.isRedeemable(now))
                .orElseThrow(() -> ApiException.of(ErrorCode.INVITATION_INVALID));

        return redeem(invitation, user, now, request);
    }

    /**
     * Creates the invited account, <b>already verified</b>: the token was mailed only to that address,
     * which is the proof verification gives. The address is the invitation's, never the request's —
     * that binding plus {@code createVerifiedLocalUser}'s existing-account guard is this path's security.
     */
    @Transactional
    public AuthenticatedSession acceptWithNewLocalUser(String plaintextToken, String fullName,
                                                       String password, HttpServletRequest request) {
        Instant now = Instant.now();
        Invitation invitation = resolveRedeemable(plaintextToken, now);
        rateLimit.checkSignup(invitation.getEmail(), request);

        // Bound to the invited address; an existing account is sent to log in, not given a second identity.
        User user = authentication.createVerifiedLocalUser(
                invitation.getEmail(), fullName, password, request);
        WorkspaceMember member = redeem(invitation, user, now, request);

        return tokens.issue(user, member, request);
    }

    /** The email must match the invited address: a forwarded link must not let somebody else in. */
    private WorkspaceMember redeem(Invitation invitation, User user, Instant now,
                                   HttpServletRequest request) {
        if (!user.getEmail().equalsIgnoreCase(invitation.getEmail())) {
            audit.event(WorkspaceEventType.INVITATION_ACCEPTED).failed().actor(user.getId())
                    .workspace(invitation.getWorkspaceId()).from(request)
                    .reason("email_mismatch").record();
            throw new ApiException(ErrorCode.INVITATION_INVALID,
                    "Invitation was addressed to a different email");
        }

        // An unverified address is an unproven claim; accepting on it would let an interceptor walk in.
        if (!user.isEmailVerified()) {
            throw ApiException.of(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        if (members.findByUserIdAndStatus(user.getId(), MemberStatus.ACTIVE).isPresent()) {
            throw ApiException.of(ErrorCode.ALREADY_IN_WORKSPACE);
        }

        invitation.accept(user.getId(), now);
        WorkspaceMember member = members.save(WorkspaceMember.invite(
                invitation.getWorkspaceId(), user.getId(), Set.of(invitation.getRole()),
                invitation.getInvitedBy()));

        log.info("User {} accepted invitation to workspace {} as {}",
                user.getId(), invitation.getWorkspaceId(), invitation.getRole().getName());

        audit.event(WorkspaceEventType.INVITATION_ACCEPTED)
                .actor(user.getId()).workspace(invitation.getWorkspaceId())
                .target("invitation", invitation.getId()).from(request)
                .detail("role", invitation.getRole().getName())
                .record();

        // Within this transaction, so membership and representative activation commit together.
        if (invitation.getClientId() != null) {
            events.publishEvent(new ClientRepresentativeAcceptedEvent(
                    invitation.getWorkspaceId(), invitation.getClientId(), user.getEmail(), user.getId()));
        }

        return member;
    }

    /** Unknown or consumed is {@code INVITATION_INVALID}; lapsed is {@code INVITATION_EXPIRED}. */
    private Invitation resolveRedeemable(String plaintextToken, Instant now) {
        Invitation invitation = invitations.findByTokenHash(Tokens.hash(plaintextToken))
                .orElseThrow(() -> ApiException.of(ErrorCode.INVITATION_INVALID));
        if (!invitation.isRedeemable(now)) {
            throw ApiException.of(invitation.getExpiresAt().isBefore(now)
                    ? ErrorCode.INVITATION_EXPIRED
                    : ErrorCode.INVITATION_INVALID);
        }
        return invitation;
    }

    private User requireUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.INVALID_CREDENTIALS));
    }
}
