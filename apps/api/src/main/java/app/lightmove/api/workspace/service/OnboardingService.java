package app.lightmove.api.workspace.service;
import app.lightmove.api.workspace.model.CreateWorkspaceCommand;

import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.audit.constant.AuditEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.email.service.EmailAddressValidator;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.model.PendingOnboarding;
import app.lightmove.api.workspace.constant.PendingOnboardingKind;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.constant.WorkspaceRole;
import app.lightmove.api.workspace.constant.WorkspaceStatus;
import app.lightmove.api.workspace.repository.PendingOnboardingRepository;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signup step 2: how a user ends up in a workspace.
 *
 * <p>There are three routes in, and they differ only in who decided:
 *
 * <ul>
 *   <li><b>Create one</b> — you are the first, or you want your own. You are its ADMIN.
 *   <li><b>Ask to join</b> — you found a workspace on your email domain. An admin must approve you, and
 *       until they do you have no access to anything.
 *   <li><b>Be invited</b> — an admin named you. You are in immediately; their naming you was the
 *       decision. (See {@code InvitationService}.)
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OnboardingService {

    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository members;
    private final PendingOnboardingRepository pending;
    private final UserRepository users;
    private final EmailAddressValidator emailValidator;
    private final AuditService audit;
    private final LightMoveProperties properties;

    /**
     * The workspaces already running on this user's email domain — "did my firm already sign up?"
     *
     * <p>Empty for a consumer domain even if consumer domains are permitted, because the answer for
     * {@code gmail.com} would be every unrelated customer who also used Gmail. See
     * {@link EmailAddressValidator#canGroupColleaguesBy}.
     */
    @Transactional(readOnly = true)
    public List<Workspace> joinableWorkspaces(UUID userId) {
        User user = requireUser(userId);
        String domain = EmailAddressValidator.domainOf(user.getEmail());

        if (!emailValidator.canGroupColleaguesBy(domain)) {
            return List.of();
        }
        return workspaces.findByEmailDomainAndStatus(domain, WorkspaceStatus.ACTIVE);
    }

    /**
     * Signup step 2, path A — "create my own".
     *
     * <p>Creates the workspace if the user has verified their address. If they have not, the wizard is
     * <b>held</b> and an empty Optional comes back: they carry on to step 3, and the workspace is
     * created the moment they click the link in their inbox.
     *
     * <p>The holding is not politeness about a half-finished form. The email domain is our only evidence
     * that someone works at a firm, so a workspace bound to {@code goldmansachs.com} must not exist
     * because somebody typed that address into a signup form and never opened the mailbox. If it did,
     * every real employee of that firm would later be shown the impostor's organisation as "your firm is
     * already here" — and asking to join it would leave them pending forever, because its only admin can
     * never verify. Nothing on the domain exists until the mailbox is proved.
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

        members.save(WorkspaceMember.invite(workspace.getId(), userId, WorkspaceRole.ADMIN, userId));

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
        requireAdmin(userId, workspaceId);

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
     * Asks to join an existing workspace. Lands as {@link MemberStatus#PENDING_APPROVAL} — no access
     * until an admin says yes.
     *
     * <p>The requested role is a suggestion, not a grant. The approving admin chooses the real one,
     * which is what stops someone walking in and declaring themselves an ADMIN.
     */
    @Transactional
    public Optional<WorkspaceMember> requestToJoin(UUID userId, UUID workspaceId,
                                                   WorkspaceRole requestedRole,
                                                   HttpServletRequest request) {
        User user = requireUser(userId);
        requireNoExistingMembership(userId);

        Workspace workspace = workspaces.findById(workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.WORKSPACE_NOT_FOUND));

        // You may only ask to join a workspace on your own domain. Without this, anyone could enumerate
        // workspace ids and pepper unrelated firms with join requests.
        //
        // Checked even for the held case: a request that could never be granted should be refused now,
        // not silently stored and refused in three days' time when they finally click the link.
        //
        // Served as 403 rather than 404 only because they had to have been shown this id to reach here;
        // a stranger guessing ids gets nothing useful either way.
        if (!workspace.owns(user.getEmail())) {
            throw new ApiException(ErrorCode.JOIN_DOMAIN_MISMATCH,
                    "User %s is not on domain %s".formatted(userId, workspace.getEmailDomain()));
        }

        // Asked before and was told no. Re-asking is not a path back in — an admin has to invite them.
        members.findByWorkspaceIdAndUserId(workspaceId, userId).ifPresent(existing -> {
            throw ApiException.of(switch (existing.getStatus()) {
                case PENDING_APPROVAL -> ErrorCode.JOIN_REQUEST_PENDING;
                case REJECTED -> ErrorCode.JOIN_REQUEST_REJECTED;
                default -> ErrorCode.ALREADY_IN_WORKSPACE;
            });
        });

        // Held, for the same reason a creation is: an admin's approval queue is not a place to put
        // people who have not shown they read the mailbox they claim as their evidence of employment.
        if (!user.isEmailVerified()) {
            holdJoin(userId, workspaceId, requestedRole);
            log.info("Held join request from unverified user {} for workspace {}", userId, workspaceId);
            return Optional.empty();
        }

        return Optional.of(commitJoin(userId, workspaceId, requestedRole, request));
    }

    private WorkspaceMember commitJoin(UUID userId, UUID workspaceId, WorkspaceRole requestedRole,
                                       HttpServletRequest request) {
        WorkspaceMember member = members.save(
                WorkspaceMember.requestToJoin(workspaceId, userId, requestedRole));

        log.info("User {} asked to join workspace {}", userId, workspaceId);
        audit.event(AuditEventType.JOIN_REQUESTED)
                .actor(userId).workspace(workspaceId).target("member", member.getId()).from(request)
                .detail("requestedRole", member.getRole().name())
                .record();

        return member;
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
        pending.findByUserId(userId)
                .filter(held -> held.getKind() == PendingOnboardingKind.CREATE)
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
     * can no longer be honoured (it expired, they joined somewhere else in the meantime, the workspace
     * they wanted to join has since been deleted), the intent is dropped and verification still stands.
     * The alternative is a user who clicks a valid link and is told their email could not be verified,
     * for reasons that have nothing to do with their email.
     *
     * @return the invitations to send, now that there is a workspace to send them for. Empty unless a
     *         CREATE was materialised.
     */
    @Transactional
    public Optional<Materialised> materialise(UUID userId, HttpServletRequest request) {
        PendingOnboarding held = pending.findByUserId(userId).orElse(null);
        if (held == null) {
            return Optional.empty();
        }

        pending.delete(held);

        if (held.isExpired(Instant.now())) {
            log.info("Discarding an expired held wizard for user {}", userId);
            return Optional.empty();
        }
        if (members.findByUserIdAndStatus(userId, MemberStatus.ACTIVE).isPresent()) {
            log.info("Discarding a held wizard for user {} — they are already in a workspace", userId);
            return Optional.empty();
        }

        User user = requireUser(userId);

        if (held.getKind() == PendingOnboardingKind.JOIN) {
            if (workspaces.findById(held.getWorkspaceId()).isEmpty()) {
                log.info("Discarding a held join for user {} — workspace {} is gone",
                        userId, held.getWorkspaceId());
                return Optional.empty();
            }
            commitJoin(userId, held.getWorkspaceId(), held.getRequestedRole(), request);
            return Optional.of(new Materialised(null, List.of()));
        }

        Workspace workspace = commitCreate(user, new CreateWorkspaceCommand(
                held.getName(), held.getCompanySize(), held.getPrimaryRegion(),
                held.getJobTitle(), held.getTeamFocus()), request);

        return Optional.of(new Materialised(workspace, held.getInvitations()));
    }

    /**
     * Records what they asked for at step 2, or amends it if they went Back and changed their mind.
     *
     * <p>Built complete and saved once, never saved empty and filled in afterwards: the table's CHECK
     * constraints require a CREATE row to carry a name and a JOIN row to carry a workspace, and a
     * half-built insert is rejected by the database — as it should be.
     */
    private void holdCreate(UUID userId, CreateWorkspaceCommand command) {
        pending.findByUserId(userId).ifPresentOrElse(
                held -> held.describe(command.name().trim(), command.companySize(),
                        command.primaryRegion(), command.teamFocus(), command.jobTitle()),
                () -> pending.save(PendingOnboarding.toCreate(userId, command.name().trim(),
                        command.companySize(), command.primaryRegion(), command.teamFocus(),
                        command.jobTitle(), expiry())));
    }

    private void holdJoin(UUID userId, UUID workspaceId, WorkspaceRole requestedRole) {
        pending.findByUserId(userId).ifPresentOrElse(
                held -> held.redirectToJoin(workspaceId, requestedRole),
                () -> pending.save(PendingOnboarding.toJoin(userId, workspaceId, requestedRole, expiry())));
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

    /** Pending join requests, for the admin who has to decide on them. */
    @Transactional(readOnly = true)
    public List<WorkspaceMember> pendingRequests(UUID workspaceId) {
        return members.findByWorkspaceIdAndStatus(workspaceId, MemberStatus.PENDING_APPROVAL);
    }

    /**
     * An admin lets someone in. This is the moment a person gains access to a firm's candidate data, so
     * it is authorised strictly and audited loudly.
     */
    @Transactional
    public WorkspaceMember approve(UUID adminUserId, UUID workspaceId, UUID memberId,
                                   WorkspaceRole grantedRole, HttpServletRequest request) {
        requireAdmin(adminUserId, workspaceId);

        WorkspaceMember member = requirePendingMember(workspaceId, memberId);
        member.approve(adminUserId, grantedRole);

        log.info("User {} approved member {} into workspace {} as {}",
                adminUserId, member.getUserId(), workspaceId, member.getRole());

        audit.event(AuditEventType.JOIN_APPROVED)
                .actor(adminUserId).workspace(workspaceId).target("member", memberId).from(request)
                .detail("approvedUserId", member.getUserId().toString())
                .detail("grantedRole", member.getRole().name())
                .record();

        return member;
    }

    @Transactional
    public WorkspaceMember reject(UUID adminUserId, UUID workspaceId, UUID memberId,
                                  HttpServletRequest request) {
        requireAdmin(adminUserId, workspaceId);

        WorkspaceMember member = requirePendingMember(workspaceId, memberId);
        member.reject(adminUserId);

        audit.event(AuditEventType.JOIN_REJECTED)
                .actor(adminUserId).workspace(workspaceId).target("member", memberId).from(request)
                .detail("rejectedUserId", member.getUserId().toString())
                .record();

        return member;
    }

    /**
     * Re-checked here and not merely trusted from the JWT's role claim, because the token was minted up
     * to 15 minutes ago and the admin may have been demoted since. For an action that hands over access
     * to candidate PII, a stale claim is not good enough.
     */
    private void requireAdmin(UUID userId, UUID workspaceId) {
        WorkspaceMember admin = members
                .findByWorkspaceIdAndUserIdAndStatus(workspaceId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_A_MEMBER));

        if (admin.getRole() != WorkspaceRole.ADMIN) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Only an admin may decide join requests");
        }
    }

    /** Scoped by workspace, so an admin of one firm cannot approve a member into another. */
    private WorkspaceMember requirePendingMember(UUID workspaceId, UUID memberId) {
        WorkspaceMember member = members.findById(memberId)
                .filter(m -> m.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_A_MEMBER));

        if (!member.isPending()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Membership is not pending, it is " + member.getStatus());
        }
        return member;
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
