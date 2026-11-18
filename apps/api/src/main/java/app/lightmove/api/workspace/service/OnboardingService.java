package app.lightmove.api.workspace.service;

import app.lightmove.api.billing.trial.service.WorkspaceTrials;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LegalSettings;
import app.lightmove.api.core.config.LightMoveProperties;
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
import app.lightmove.api.core.tenant.WorkspaceScope;
import app.lightmove.api.core.security.membership.MemberStatus;
import app.lightmove.api.workspace.model.CreateWorkspaceCommand;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.core.security.membership.WorkspaceMember;
import app.lightmove.api.core.security.membership.WorkspaceMemberRepository;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
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
    private final WorkspaceSettingsService settings;
    private final WorkspaceTrials trials;
    private final Clock clock;
    private final LightMoveProperties properties;

    /** Signup's door, gated on verification alone — so shut to anyone already in a workspace. */
    @Transactional
    public Workspace createFirstWorkspace(UUID userId, CreateWorkspaceCommand command, boolean dpaAccepted,
                                          HttpServletRequest request) {
        // Locked before the membership is read. Read first, a submit committing between that read and the account's
        // load left the check seeing no workspace and the row already at its new version, so @Version passed too and
        // a double-clicked step founded two workspaces.
        users.lockToFoundFirstWorkspace(userId).orElseThrow(() -> ApiException.of(ErrorCode.INVALID_CREDENTIALS));
        requireNoExistingMembership(userId);
        return createWorkspace(userId, command, dpaAccepted, request);
    }

    /**
     * The caller is verified ({@code SecurityConfig} refuses {@code /onboarding/**} otherwise). The
     * domain comes from the user's own address, never the request, so nobody can claim another firm's. Where the
     * deployment names a data processing agreement, the founder accepts it for the firm here — both doors pass this
     * way, so neither can found a workspace without it.
     */
    @Transactional
    public Workspace createWorkspace(UUID userId, CreateWorkspaceCommand command, boolean dpaAccepted,
                                     HttpServletRequest request) {
        User user = requireUser(userId);
        LegalSettings legal = properties.legal();
        if (legal.asksForDpa() && !dpaAccepted) {
            throw ApiException.of(ErrorCode.DPA_ACCEPTANCE_REQUIRED);
        }
        rateLimit.checkWorkspaceCreation(user.getEmail(), request);

        String domain = EmailAddressValidator.domainOf(user.getEmail());
        WorkspaceIdentity identity = companyResolver.resolve(command.name(), command.apolloAccountId());
        String slug = SlugGenerator.from(identity.name(), workspaces::existsBySlug);

        Workspace founded = Workspace.create(identity.name(), slug, domain, userId, command.mode(),
                identity.company());
        if (legal.asksForDpa()) {
            founded.recordDpaAcceptance(legal.dpaVersion(), userId, clock.instant());
        }
        Workspace workspace = workspaces.save(founded);

        selection.remember(user, members.save(WorkspaceMember.invite(
                workspace.getId(), userId, Set.of(rbac.role(WorkspaceRole.ADMIN)), userId)));
        // Flushed inside the scope: written at commit, the trial would be checked against the caller's workspace.
        WorkspaceScope.runInJoinedTransaction(workspace.getId(), () -> {
            trials.start(workspace.getId(), userId);
            workspaces.flush();
        });

        log.info("Workspace {} ({}) created by user {} on domain {}", workspace.getId(), slug, userId, domain);
        audit.event(WorkspaceEventType.WORKSPACE_CREATED)
                .actor(userId).workspace(workspace.getId()).from(request)
                .detail("domain", domain).detail("slug", slug).detail("mode", workspace.getMode().name())
                .detailIfPresent("dpaVersion", workspace.getDpaVersion())
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
        workspace.describe(identity.name(), identity.company());
        // Through the settings' switch, not describe: an admin can call this route at any time, and a mode
        // changed here must leave the same audit record as one changed in Settings.
        settings.changeMode(userId, workspaceId, command.mode(), request);

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
