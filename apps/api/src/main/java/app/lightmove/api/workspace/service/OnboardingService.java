package app.lightmove.api.workspace.service;

import app.lightmove.api.core.audit.constant.AuditEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.email.service.EmailAddressValidator;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.RbacService;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.model.CreateWorkspaceCommand;
import app.lightmove.api.workspace.model.PendingOnboarding;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.repository.PendingOnboardingRepository;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signup step 2: how a user ends up in a workspace.
 *
 * <p>There are exactly two routes in, and they differ only in who decided:
 *
 * <ul>
 *   <li><b>Create one</b> — signup always ends here. You are the workspace's ADMIN.
 *   <li><b>Be invited</b> — an admin named you. You are in immediately; their naming you was the
 *       decision. (See {@code InvitationService}.)
 * </ul>
 *
 * <p>There is deliberately no "ask to join". Membership is invitation-only: finding a workspace on
 * your email domain proves you share an employer's mail system, not that you should see an
 * executive-search pipeline — so signup does not even look. The admin reaches out, or you create your
 * own workspace.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OnboardingService {

    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository members;
    private final PendingOnboardingRepository pendingOnboardings;
    private final UserRepository users;
    private final WorkspaceAccess access;
    private final RbacService rbac;
    private final AuditService audit;
    private final LightMoveProperties properties;

    /**
     * Signup step 2 — "create my workspace".
     *
     * <p>Creates the workspace if the user has verified their address. If they have not, the wizard is
     * <b>held</b> and an empty Optional comes back: they carry on to step 3, and the workspace is
     * created the moment they click the link in their inbox.
     *
     * <p>The holding is not politeness about a half-finished form. The email domain is our only evidence
     * that someone works at a firm, so a workspace bound to {@code goldmansachs.com} must not exist
     * because somebody typed that address into a signup form and never opened the mailbox. Nothing on
     * the domain exists until the mailbox is proved.
     *
     * <p>The domain itself is taken from the user's own address, never from the request — that is the
     * difference between recording which firm a workspace belongs to and letting anyone claim any
     * company's by typing it into a form.
     *
     * @return the workspace, or empty if it is waiting on email verification.
     */
    @Transactional
    public Optional<Workspace> createWorkspace(UUID userId, CreateWorkspaceCommand command,
                                               HttpServletRequest request) {
        User user = requireUser(userId);
        requireNoExistingMembership(userId);

        if (!user.isEmailVerified()) {
            holdCreate(userId, command);
            log.info("Held workspace creation for unverified user {}", userId);
            return Optional.empty();
        }

        return Optional.of(commitCreate(user, command, request));
    }

    /** The act itself, once someone is entitled to it. Reached from step 2, or from verification. */
    private Workspace commitCreate(User user, CreateWorkspaceCommand command, HttpServletRequest request) {
        UUID userId = user.getId();
        String domain = EmailAddressValidator.domainOf(user.getEmail());
        String slug = SlugGenerator.from(command.name(), workspaces::existsBySlug);

        Workspace workspace = workspaces.save(Workspace.create(
                command.name().trim(), slug, domain, userId,
                command.companySize(), command.primaryRegion(), command.teamFocus()));

        // "Your role" from the wizard is a job title, and belongs on the person. Authority is separate:
        // whoever creates the workspace runs it.
        user.setTitle(command.jobTitle());

        members.save(WorkspaceMember.invite(
                workspace.getId(), userId, Set.of(rbac.role(WorkspaceRole.ADMIN)), userId));

        log.info("Workspace {} ({}) created by user {} on domain {}", workspace.getId(), slug, userId, domain);
        audit.event(AuditEventType.WORKSPACE_CREATED)
                .actor(userId).workspace(workspace.getId()).from(request)
                .detail("domain", domain).detail("slug", slug)
                .record();

        return workspace;
    }

    /**
     * Corrects the details of a workspace the caller already runs.
     *
     * <p>This exists because signup step 2 <i>commits</i>. The mockup's wizard keeps its steps in the
     * browser, so its Back button is free; ours creates a real workspace at step 2, and a Back button
     * that dropped the user on an empty create form would only ever produce "you already have a
     * workspace". Going back has to mean editing what is already there — which is what a user pressing
     * Back actually wants, and is a thing they will want again from Settings.
     *
     * <p>Admin only, and the role is re-read from the database rather than taken from the caller's JWT:
     * that claim was minted up to fifteen minutes ago and may since have been revoked.
     */
    @Transactional
    public Workspace updateWorkspace(UUID userId, UUID workspaceId, CreateWorkspaceCommand command,
                                     HttpServletRequest request) {
        access.requireAdmin(userId, workspaceId);

        Workspace workspace = workspaces.findById(workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.WORKSPACE_NOT_FOUND));

        workspace.describe(command.name().trim(), command.companySize(),
                command.primaryRegion(), command.teamFocus());

        // "Your role" is the person's job title, and it travels with them, not with the workspace.
        requireUser(userId).setTitle(command.jobTitle());

        audit.event(AuditEventType.WORKSPACE_UPDATED)
                .actor(userId).workspace(workspaceId).from(request)
                .detail("name", workspace.getName())
                .record();

        return workspace;
    }

    /**
     * Step 3 — the invitations, held with the rest of the wizard when there is no workspace yet.
     *
     * @return false when they were sent immediately (the user is verified and has a workspace), true
     *         when they were held until verification.
     */
    @Transactional
    public boolean holdInvitations(UUID userId, List<PendingOnboarding.PendingInvite> invites) {
        User user = requireUser(userId);
        if (user.isEmailVerified()) {
            return false;
        }
        // Held rather than sent, because "send an email to these five people" is an action LightMove
        // takes on the user's word — and their word is exactly what has not been checked yet. An
        // unverified account must not be able to make us mail strangers.
        //
        // They belong to the organisation being created, so there must be one. A user who reached step 3
        // without a held CREATE has nothing to invite anyone into; nothing is stored, and the invitations
        // are simply not sent — which is already true, and is what the 202 says.
        pendingOnboardings.findByUserId(userId)
                .ifPresentOrElse(
                        held -> held.holdInvitations(invites),
                        () -> log.warn("Invitations from user {} with no held organisation — dropped", userId));
        return true;
    }

    /**
     * The moment the wizard becomes real: the user has proved the mailbox, so what they asked for at
     * step 2 can now be done in their name.
     *
     * <p>Deliberately forgiving. Verification is the user's act and must succeed — if the held intent
     * can no longer be honoured (it expired, they accepted an invitation in the meantime), the intent
     * is dropped and verification still stands. The alternative is a user who clicks a valid link and
     * is told their email could not be verified, for reasons that have nothing to do with their email.
     *
     * @return the created workspace and the invitations to send for it, now that it exists.
     */
    @Transactional
    public Optional<Materialised> materialise(UUID userId, HttpServletRequest request) {
        PendingOnboarding held = pendingOnboardings.findByUserId(userId).orElse(null);
        if (held == null) {
            return Optional.empty();
        }

        pendingOnboardings.delete(held);

        if (held.isExpired(Instant.now())) {
            log.info("Discarding an expired held wizard for user {}", userId);
            return Optional.empty();
        }
        if (members.findByUserIdAndStatus(userId, MemberStatus.ACTIVE).isPresent()) {
            log.info("Discarding a held wizard for user {} — they are already in a workspace", userId);
            return Optional.empty();
        }

        User user = requireUser(userId);

        Workspace workspace = commitCreate(user, new CreateWorkspaceCommand(
                held.getName(), held.getCompanySize(), held.getPrimaryRegion(),
                held.getJobTitle(), held.getTeamFocus()), request);

        return Optional.of(new Materialised(workspace, held.getInvitations()));
    }

    /**
     * Records what they asked for at step 2, or amends it if they went Back and changed their mind.
     *
     * <p>Built complete and saved once, never saved empty and filled in afterwards: the table requires
     * a held wizard to carry a name, and a half-built insert is rejected by the database — as it
     * should be.
     */
    private void holdCreate(UUID userId, CreateWorkspaceCommand command) {
        pendingOnboardings.findByUserId(userId).ifPresentOrElse(
                held -> held.describe(command.name().trim(), command.companySize(),
                        command.primaryRegion(), command.teamFocus(), command.jobTitle()),
                () -> pendingOnboardings.save(PendingOnboarding.toCreate(userId, command.name().trim(),
                        command.companySize(), command.primaryRegion(), command.teamFocus(),
                        command.jobTitle(), expiry())));
    }

    /**
     * The held wizard dies with the verification link that would have redeemed it. The intent and the
     * proof of intent have the same lifetime: a link that no longer works must not be able to bring a
     * three-week-old draft organisation to life.
     */
    private Instant expiry() {
        return Instant.now().plus(properties.auth().verificationTokenTtl());
    }

    /** What verification turned a held wizard into, so the caller can finish the job (send the invites). */
    public record Materialised(Workspace workspace, List<PendingOnboarding.PendingInvite> invitations) {
    }

    private void requireNoExistingMembership(UUID userId) {
        if (members.findByUserIdAndStatus(userId, MemberStatus.ACTIVE).isPresent()) {
            throw ApiException.of(ErrorCode.ALREADY_IN_WORKSPACE);
        }
    }

    private User requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.of(ErrorCode.INVALID_CREDENTIALS));
    }
}
