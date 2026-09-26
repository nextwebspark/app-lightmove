package app.lightmove.api.workspace.service;

import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.email.service.EmailAddressValidator;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.RbacService;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.security.service.WorkspaceSelection;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.model.CreateWorkspaceCommand;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * How a user ends up in a workspace: creating one — at signup, or later from Settings → Workspaces —
 * where they are its ADMIN, or being invited, where the admin naming them was the decision.
 *
 * <p>There is deliberately no "ask to join". Finding a workspace on your email domain proves you share
 * an employer's mail system, not that you should see an executive-search pipeline — so signup does not
 * look.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OnboardingService {

    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository members;
    private final UserRepository users;
    private final WorkspaceAccess access;
    private final RbacService rbac;
    private final AuditService audit;
    private final WorkspaceCompanyResolver companyResolver;
    private final WorkspaceSelection selection;
    private final RateLimitGuard rateLimit;

    /**
     * Signup's organisation step. {@code /onboarding/**} is gated on verification alone, so this door
     * must stay shut to anyone already in a workspace: a client representative's portal seat would
     * otherwise found a firm here around the staff gate on {@code POST /workspaces}.
     */
    @Transactional
    public Workspace createFirstWorkspace(UUID userId, CreateWorkspaceCommand command,
                                          HttpServletRequest request) {
        if (!members.findAllByUserIdAndStatusOrderByJoinedAtAsc(userId, MemberStatus.ACTIVE).isEmpty()) {
            throw ApiException.of(ErrorCode.ALREADY_IN_WORKSPACE);
        }
        return createWorkspace(userId, command, request);
    }

    /**
     * Creates a workspace with the caller as its ADMIN. Verification is signup's step 2, so the caller
     * is already verified; {@code SecurityConfig} refuses {@code /onboarding/**} to an unverified session.
     *
     * <p>The domain is taken from the user's own address, never from the request — that is the
     * difference between recording which firm a workspace belongs to and letting anyone claim any
     * company's by typing it into a form.
     *
     * <p>The new workspace becomes the one the next sign-in opens in; the session that created it
     * still has to switch (or refresh) to carry it, since the token it holds was minted before.
     */
    @Transactional
    public Workspace createWorkspace(UUID userId, CreateWorkspaceCommand command,
                                     HttpServletRequest request) {
        User user = requireUser(userId);
        rateLimit.checkWorkspaceCreation(user.getEmail(), request);

        String domain = EmailAddressValidator.domainOf(user.getEmail());
        WorkspaceIdentity identity = companyResolver.resolve(command.name(), command.apolloAccountId());
        String slug = SlugGenerator.from(identity.name(), workspaces::existsBySlug);

        Workspace workspace = workspaces.save(Workspace.create(
                identity.name(), slug, domain, userId, identity.company(),
                command.companySize(), command.primaryRegion(), command.teamFocus()));

        WorkspaceMember member = members.save(WorkspaceMember.invite(
                workspace.getId(), userId, Set.of(rbac.role(WorkspaceRole.ADMIN)), userId));
        selection.remember(user, member);

        log.info("Workspace {} ({}) created by user {} on domain {}", workspace.getId(), slug, userId, domain);
        audit.event(WorkspaceEventType.WORKSPACE_CREATED)
                .actor(userId).workspace(workspace.getId()).from(request)
                .detail("domain", domain).detail("slug", slug)
                .record();

        return workspace;
    }

    /**
     * Corrects the details of a workspace the caller already runs.
     *
     * <p>The organisation step <i>commits</i>, so a Back button that dropped the user on an empty
     * create form would only ever produce "you already have a workspace". Going back means editing
     * what is already there.
     *
     * <p>Admin only, and the role is re-read from the database rather than taken from the caller's
     * JWT: that claim was minted up to fifteen minutes ago and may since have been revoked.
     */
    @Transactional
    public Workspace updateWorkspace(UUID userId, UUID workspaceId, CreateWorkspaceCommand command,
                                     HttpServletRequest request) {
        access.requireAdmin(userId, workspaceId);

        Workspace workspace = workspaces.findById(workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.WORKSPACE_NOT_FOUND));

        WorkspaceIdentity identity = companyResolver.resolve(command.name(), command.apolloAccountId());
        workspace.describe(identity.name(), identity.company(), command.companySize(),
                command.primaryRegion(), command.teamFocus());

        audit.event(WorkspaceEventType.WORKSPACE_UPDATED)
                .actor(userId).workspace(workspaceId).from(request)
                .detail("name", workspace.getName())
                .record();

        return workspace;
    }

    private User requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.of(ErrorCode.INVALID_CREDENTIALS));
    }
}
