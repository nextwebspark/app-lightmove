package app.lightmove.api.workspace.service;

import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
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
 * How a user ends up in a workspace: creating one at signup, where they are its ADMIN, or being
 * invited, where the admin naming them was the decision.
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

    /**
     * Signup step 3 — "create my workspace". Verification is step 2, so the caller is already verified;
     * {@code SecurityConfig} refuses {@code /onboarding/**} to an unverified session.
     *
     * <p>The domain is taken from the user's own address, never from the request — that is the
     * difference between recording which firm a workspace belongs to and letting anyone claim any
     * company's by typing it into a form.
     */
    @Transactional
    public Workspace createWorkspace(UUID userId, CreateWorkspaceCommand command,
                                     HttpServletRequest request) {
        User user = requireUser(userId);
        requireNoExistingMembership(userId);

        String domain = EmailAddressValidator.domainOf(user.getEmail());
        String slug = SlugGenerator.from(command.name(), workspaces::existsBySlug);

        Workspace workspace = workspaces.save(Workspace.create(
                command.name().trim(), slug, domain, userId,
                command.companySize(), command.primaryRegion(), command.teamFocus()));

        members.save(WorkspaceMember.invite(
                workspace.getId(), userId, Set.of(rbac.role(WorkspaceRole.ADMIN)), userId));

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

        workspace.describe(command.name().trim(), command.companySize(),
                command.primaryRegion(), command.teamFocus());

        audit.event(WorkspaceEventType.WORKSPACE_UPDATED)
                .actor(userId).workspace(workspaceId).from(request)
                .detail("name", workspace.getName())
                .record();

        return workspace;
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
