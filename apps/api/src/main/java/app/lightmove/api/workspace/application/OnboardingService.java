package app.lightmove.api.workspace.application;

import app.lightmove.api.auth.domain.User;
import app.lightmove.api.auth.infrastructure.UserRepository;
import app.lightmove.api.common.audit.AuditEventType;
import app.lightmove.api.common.audit.AuditService;
import app.lightmove.api.common.error.ApiException;
import app.lightmove.api.common.error.ErrorCode;
import app.lightmove.api.email.EmailAddressValidator;
import app.lightmove.api.workspace.domain.MemberStatus;
import app.lightmove.api.workspace.domain.Workspace;
import app.lightmove.api.workspace.domain.WorkspaceMember;
import app.lightmove.api.workspace.domain.WorkspaceRole;
import app.lightmove.api.workspace.domain.WorkspaceStatus;
import app.lightmove.api.workspace.infrastructure.WorkspaceMemberRepository;
import app.lightmove.api.workspace.infrastructure.WorkspaceRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class OnboardingService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingService.class);

    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository members;
    private final UserRepository users;
    private final EmailAddressValidator emailValidator;
    private final AuditService audit;

    public OnboardingService(WorkspaceRepository workspaces, WorkspaceMemberRepository members,
                             UserRepository users, EmailAddressValidator emailValidator, AuditService audit) {
        this.workspaces = workspaces;
        this.members = members;
        this.users = users;
        this.emailValidator = emailValidator;
        this.audit = audit;
    }

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
     * Creates a workspace and makes its creator an ADMIN.
     *
     * <p>The domain is taken from the user's own address, never from the request — that is the
     * difference between recording which firm a workspace belongs to and letting anyone claim any
     * company's domain by typing it into a form.
     */
    @Transactional
    public Workspace createWorkspace(UUID userId, CreateWorkspaceCommand command, HttpServletRequest request) {
        User user = requireUser(userId);
        requireNoExistingMembership(userId);

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
     * Asks to join an existing workspace. Lands as {@link MemberStatus#PENDING_APPROVAL} — no access
     * until an admin says yes.
     *
     * <p>The requested role is a suggestion, not a grant. The approving admin chooses the real one,
     * which is what stops someone walking in and declaring themselves an ADMIN.
     */
    @Transactional
    public WorkspaceMember requestToJoin(UUID userId, UUID workspaceId, WorkspaceRole requestedRole,
                                         HttpServletRequest request) {
        User user = requireUser(userId);
        requireNoExistingMembership(userId);

        Workspace workspace = workspaces.findById(workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.WORKSPACE_NOT_FOUND));

        // You may only ask to join a workspace on your own domain. Without this, anyone could enumerate
        // workspace ids and pepper unrelated firms with join requests.
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

        WorkspaceMember member = members.save(
                WorkspaceMember.requestToJoin(workspaceId, userId, requestedRole));

        log.info("User {} asked to join workspace {}", userId, workspaceId);
        audit.event(AuditEventType.JOIN_REQUESTED)
                .actor(userId).workspace(workspaceId).target("member", member.getId()).from(request)
                .detail("requestedRole", member.getRole().name())
                .record();

        return member;
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
