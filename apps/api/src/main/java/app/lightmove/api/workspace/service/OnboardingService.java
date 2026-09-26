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
 * Creating a workspace — at signup or from Settings — as its ADMIN. Deliberately no "ask to join":
 * sharing an email domain does not entitle anyone to a firm's pipeline, so signup does not look.
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

    /** Signup's door, gated on verification alone — so shut to anyone already in a workspace. */
    @Transactional
    public Workspace createFirstWorkspace(UUID userId, CreateWorkspaceCommand command,
                                          HttpServletRequest request) {
        requireNoExistingMembership(userId);
        return createWorkspace(userId, command, request);
    }

    /**
     * The caller is verified ({@code SecurityConfig} refuses {@code /onboarding/**} otherwise). The
     * domain comes from the user's own address, never the request, so nobody can claim another firm's.
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

        selection.remember(user, members.save(WorkspaceMember.invite(
                workspace.getId(), userId, Set.of(rbac.role(WorkspaceRole.ADMIN)), userId)));

        log.info("Workspace {} ({}) created by user {} on domain {}", workspace.getId(), slug, userId, domain);
        audit.event(WorkspaceEventType.WORKSPACE_CREATED)
                .actor(userId).workspace(workspace.getId()).from(request)
                .detail("domain", domain).detail("slug", slug)
                .record();

        return workspace;
    }

    /**
     * Signup's Back, the step having committed. Admin only, re-read from the database — the JWT's roles
     * may be fifteen minutes stale.
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

    private void requireNoExistingMembership(UUID userId) {
        if (!members.findAllByUserIdAndStatusOrderByJoinedAtAsc(userId, MemberStatus.ACTIVE).isEmpty()) {
            throw ApiException.of(ErrorCode.ALREADY_IN_WORKSPACE);
        }
    }

    private User requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.of(ErrorCode.INVALID_CREDENTIALS));
    }
}
