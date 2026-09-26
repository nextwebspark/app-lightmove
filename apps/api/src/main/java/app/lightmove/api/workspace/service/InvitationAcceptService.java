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
     * What an invitation says, to whoever is holding its link — before they have an account, let alone a
     * session.
     *
     * <p>Readable unauthenticated, because the person clicking the link out of their inbox is usually
     * a stranger: the signup form needs the invited address so it can fix it rather than let them
     * create an account we would then refuse.
     *
     * <p>It discloses a workspace name, an inviter's name and the invited address to a caller holding
     * a 256-bit token mailed to that address. The email already said all three things.
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

    /** What the invitee is shown before they sign in. See {@link #preview}. */
    public record InvitationPreview(String email, String role, String workspaceName,
                                    String inviterName) {}

    /**
     * Accepts an invitation by its emailed token. The invitee lands ACTIVE straight away — no approval
     * step, because an admin naming them was the approval.
     */
    @Transactional
    public WorkspaceMember accept(String plaintextToken, UUID userId, HttpServletRequest request) {
        Instant now = Instant.now();
        Invitation invitation = resolveRedeemable(plaintextToken, now);
        return redeem(invitation, requireUser(userId), now, request);
    }

    /**
     * Accepts the caller's own outstanding invitation, with no token.
     *
     * <p>The token's only job was proving control of the invited mailbox, and an authenticated,
     * <b>email-verified</b> user whose address matches has already proven that. It is what lets an
     * invitee who verified in a fresh tab — where the emailed token lives in another tab's
     * sessionStorage — still land in the right workspace rather than create-your-own.
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
     * Accepts an invitation by creating the invited account in one step — the door in for an invitee who
     * has no account yet, which is the common case.
     *
     * <p><b>No email-verification round-trip.</b> The invitation token was mailed only to
     * {@code invitation.email}, so holding it is proof of that mailbox — the same proof verification
     * exists to give. The account's address is the invitation's, never the request's, so the token can
     * only mint the identity it was addressed to; that binding, plus the existing-account guard in
     * {@code createVerifiedLocalUser}, is the security of this path.
     *
     * <p>Plain {@code @Transactional}: the account, membership, invitation-accept and refresh token
     * roll back together.
     */
    @Transactional
    public AuthenticatedSession acceptWithNewLocalUser(String plaintextToken, String fullName,
                                                       String password, HttpServletRequest request) {
        Instant now = Instant.now();
        Invitation invitation = resolveRedeemable(plaintextToken, now);
        rateLimit.checkSignup(invitation.getEmail(), request);

        // The email is the invitation's, so the account is bound to the address the token was mailed
        // to. createVerifiedLocalUser rejects an address that already has an account, so that person
        // is sent to log in rather than silently gaining a second identity.
        User user = authentication.createVerifiedLocalUser(
                invitation.getEmail(), fullName, password, request);
        WorkspaceMember member = redeem(invitation, user, now, request);

        return tokens.issue(user, member, request);
    }

    /**
     * The shared tail of both accept paths: the guards, the membership, the audit trail.
     *
     * <p>Their email must match the address that was invited. An invitation is addressed to a person,
     * and a link forwarded to somebody else must not let that somebody else in.
     */
    private WorkspaceMember redeem(Invitation invitation, User user, Instant now,
                                   HttpServletRequest request) {
        if (!user.getEmail().equalsIgnoreCase(invitation.getEmail())) {
            audit.event(WorkspaceEventType.INVITATION_ACCEPTED).failed().actor(user.getId())
                    .workspace(invitation.getWorkspaceId()).from(request)
                    .reason("email_mismatch").record();
            throw new ApiException(ErrorCode.INVITATION_INVALID,
                    "Invitation was addressed to a different email");
        }

        // An unverified address is an unproven claim to be this person. Accepting on it would let
        // whoever intercepted the invitation email walk in as its intended recipient.
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

        // Published within this transaction, so the membership and the representative row's
        // activation commit together.
        if (invitation.getClientId() != null) {
            events.publishEvent(new ClientRepresentativeAcceptedEvent(
                    invitation.getWorkspaceId(), invitation.getClientId(), user.getEmail(), user.getId()));
        }

        return member;
    }

    /**
     * Resolves an invitation from its emailed token, or fails with the reason it cannot be redeemed: an
     * unknown or already-consumed token is {@code INVITATION_INVALID}, a lapsed one
     * {@code INVITATION_EXPIRED}. Shared by preview and every accept path.
     */
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
