package app.lightmove.api.account.service;

import app.lightmove.api.account.constant.AccountProof;
import app.lightmove.api.account.dto.AccountDeletionPreviewResponse;
import app.lightmove.api.account.dto.AccountWorkspace;
import app.lightmove.api.account.dto.DeleteAccountRequest;
import app.lightmove.api.account.dto.HandoverPosition;
import app.lightmove.api.account.model.AccountDeleted;
import app.lightmove.api.account.model.AccountHandover;
import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.email.service.EmailSender;
import app.lightmove.api.core.email.service.EmailTemplates;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.mfa.MfaCodeVerifier;
import app.lightmove.api.core.security.mfa.MfaFactor;
import app.lightmove.api.core.security.mfa.MfaFactorRepository;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.model.UserIdentity;
import app.lightmove.api.core.security.oauth.OAuthGrantRevokeReason;
import app.lightmove.api.core.security.oauth.OAuthGrantService;
import app.lightmove.api.core.security.repository.UserIdentityRepository;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.security.service.AccountCredentialErasure;
import app.lightmove.api.core.security.service.CurrentPasswordCheck;
import app.lightmove.api.core.security.token.ActiveSessionService;
import app.lightmove.api.core.tenant.WorkspaceScope;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.model.WorkspaceMember;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import app.lightmove.api.workspace.service.MemberDetachment;
import app.lightmove.api.workspace.service.MemberService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A person deleting their own account (#926): they leave every workspace through the roster's own removal, their
 * credentials go, and the account row is anonymised in place, so what they did for each workspace stays and reads as a
 * former member's. Refused while they are the only admin of a workspace or the only lead of a live position — nobody
 * else could hand those on — and asked of the owner as a change of how they sign in is: the password, and a code where
 * two-factor is on, or a sign-in moments ago for an account with no password.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionService {

    private final UserRepository users;
    private final UserIdentityRepository identities;
    private final WorkspaceMemberRepository members;
    private final WorkspaceRepository workspaces;
    private final MemberService memberService;
    private final MemberDetachment detachment;
    private final CurrentPasswordCheck currentPasswordCheck;
    private final MfaFactorRepository factors;
    private final MfaCodeVerifier codes;
    private final ActiveSessionService sessions;
    private final OAuthGrantService oauthGrants;
    private final AccountCredentialErasure credentials;
    private final List<AccountDeletionContributor> contributors;
    private final EntityManager entityManager;
    private final AuditService audit;
    private final EmailSender emailSender;
    private final EmailTemplates templates;
    private final ApplicationEventPublisher events;
    private final LightMoveProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AccountDeletionPreviewResponse preview(UUID userId, String presentedRefreshToken) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.of(ErrorCode.INVALID_CREDENTIALS));
        List<WorkspaceMember> active = members.findAllByUserIdAndStatusOrderByJoinedAtAsc(userId, MemberStatus.ACTIVE);
        Map<UUID, String> names = namesOf(active);
        AccountHandover handover = handoverOf(active, names);

        boolean hasPassword = user.hasPassword();
        return new AccountDeletionPreviewResponse(
                active.stream().map(member -> workspaceOf(member, names)).toList(),
                handover.soleAdminOf(),
                handover.soleLeadOf(),
                hasPassword ? AccountProof.PASSWORD : AccountProof.RECENT_SIGN_IN,
                hasPassword && holdsEnabledFactor(userId),
                !hasPassword && signedInRecently(userId, presentedRefreshToken),
                signInProvidersOf(userId));
    }

    @Transactional
    public void delete(UUID userId, DeleteAccountRequest request, String presentedRefreshToken,
                       HttpServletRequest httpRequest) {
        User user = users.lockToDelete(userId).orElseThrow(() -> ApiException.of(ErrorCode.INVALID_CREDENTIALS));
        requireOwner(user, request, presentedRefreshToken, httpRequest);

        List<WorkspaceMember> active = members.findAllByUserIdAndStatusOrderByJoinedAtAsc(userId, MemberStatus.ACTIVE);
        AccountHandover handover = handoverOf(active, namesOf(active));
        if (handover.isRequired()) {
            throw ApiException.withProperties(ErrorCode.ACCOUNT_HANDOVER_REQUIRED, handover.asProperties());
        }

        // Each workspace's writes are flushed inside its scope: the joined connection reads the caller's workspace
        // again once the task returns, and a write left to the commit's flush would be refused there.
        for (WorkspaceMember member : active) {
            inWorkspace(member.getWorkspaceId(), () -> {
                memberService.leaveOnAccountDeletion(member, httpRequest);
                entityManager.flush();
                return null;
            });
        }
        List<UUID> everyWorkspace = members.findAllByUserIdOrderByJoinedAtAsc(userId).stream()
                .map(WorkspaceMember::getWorkspaceId).distinct().toList();
        for (UUID workspaceId : everyWorkspace) {
            inWorkspace(workspaceId, () -> {
                contributors.forEach(contributor -> contributor.deleteAccountRowsIn(workspaceId, userId));
                entityManager.flush();
                return null;
            });
        }
        oauthGrants.revokeAllOfUser(userId, userId, OAuthGrantRevokeReason.ACCOUNT_DELETED, httpRequest);

        entityManager.flush();
        credentials.eraseAll(userId);
        String formerEmail = user.getEmail();
        String formerName = user.getFullName();
        user.anonymise(Instant.now(clock));

        log.info("User {} deleted their account, leaving {} workspaces", userId, active.size());
        audit.event(AuthEventType.ACCOUNT_DELETED).actor(userId).from(httpRequest)
                .detail("workspacesLeft", active.size())
                .record();
        emailSender.send(templates.buildAccountDeletedEmail(formerEmail, formerName));
        events.publishEvent(new AccountDeleted(userId, everyWorkspace));
    }

    private void requireOwner(User user, DeleteAccountRequest request, String presentedRefreshToken,
                              HttpServletRequest httpRequest) {
        if (!user.getStatus().canAuthenticate()) {
            audit.event(AuthEventType.ACCOUNT_DELETED).failed().actor(user.getId()).from(httpRequest)
                    .reason("status_" + user.getStatus()).record();
            throw ApiException.of(ErrorCode.ACCOUNT_SUSPENDED);
        }
        if (!user.hasPassword()) {
            requireRecentSignIn(user, presentedRefreshToken, httpRequest);
            return;
        }

        String currentPassword = request.currentPassword();
        if (currentPassword == null || currentPassword.isEmpty()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "currentPassword", "Enter your password");
        }
        currentPasswordCheck.require(user, currentPassword, AuthEventType.ACCOUNT_DELETED, httpRequest);

        MfaFactor factor = factors.findForUpdateByUserId(user.getId()).filter(MfaFactor::isEnabled).orElse(null);
        if (factor != null && codes.verify(factor, request.code(), Instant.now(clock)).isEmpty()) {
            audit.event(AuthEventType.ACCOUNT_DELETED).failed().actor(user.getId()).from(httpRequest)
                    .reason("bad_code").record();
            throw ApiException.withField(ErrorCode.MFA_CODE_INVALID,
                    "code", ErrorCode.MFA_CODE_INVALID.defaultMessage());
        }
    }

    /** An account with no password shows it is them as one turning on two-factor does: by a sign-in moments ago. */
    private void requireRecentSignIn(User user, String presentedRefreshToken, HttpServletRequest httpRequest) {
        UUID familyId = sessions.requireCurrentFamily(user.getId(), presentedRefreshToken);
        if (!isRecent(sessions.signedInAt(familyId))) {
            audit.event(AuthEventType.ACCOUNT_DELETED).failed().actor(user.getId()).from(httpRequest)
                    .reason("sign_in_not_recent").record();
            throw ApiException.of(ErrorCode.ACCOUNT_SIGN_IN_AGAIN);
        }
    }

    private boolean signedInRecently(UUID userId, String presentedRefreshToken) {
        try {
            return isRecent(sessions.signedInAt(sessions.requireCurrentFamily(userId, presentedRefreshToken)));
        } catch (ApiException noLiveSession) {
            return false;
        }
    }

    private boolean isRecent(Instant signedInAt) {
        return signedInAt != null
                && !signedInAt.isBefore(Instant.now(clock).minus(properties.auth().mfa().recentSignIn()));
    }

    private boolean holdsEnabledFactor(UUID userId) {
        return factors.findByUserId(userId).filter(MfaFactor::isEnabled).isPresent();
    }

    private List<String> signInProvidersOf(UUID userId) {
        return identities.findByUserIdOrderByLinkedAtAsc(userId).stream()
                .map(UserIdentity::getProvider)
                .filter(provider -> !UserIdentity.LOCAL_PROVIDER.equals(provider))
                .map(provider -> provider.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    /**
     * Who else could run each workspace and live position once this person is gone. Positions are read in their own
     * workspace's scope: read outside it, every one would look led by somebody else and nothing would be refused.
     */
    private AccountHandover handoverOf(List<WorkspaceMember> active, Map<UUID, String> names) {
        List<AccountWorkspace> soleAdminOf = new ArrayList<>();
        List<HandoverPosition> soleLeadOf = new ArrayList<>();
        for (WorkspaceMember member : active) {
            UUID workspaceId = member.getWorkspaceId();
            if (memberService.isSoleAdmin(member)) {
                soleAdminOf.add(workspaceOf(member, names));
            }
            inWorkspace(workspaceId, () -> detachment.positionsLedAloneBy(member.getId())).forEach(position ->
                    soleLeadOf.add(new HandoverPosition(workspaceId, names.get(workspaceId),
                            position.projectId(), position.title())));
        }
        return new AccountHandover(List.copyOf(soleAdminOf), List.copyOf(soleLeadOf));
    }

    /** Joined on purpose: the deletion reaches past the caller's workspace and must commit as one. */
    private static <T> T inWorkspace(UUID workspaceId, Supplier<T> task) {
        AtomicReference<T> answer = new AtomicReference<>();
        WorkspaceScope.runInJoinedTransaction(workspaceId, () -> answer.set(task.get()));
        return answer.get();
    }

    private Map<UUID, String> namesOf(List<WorkspaceMember> memberships) {
        return workspaces.findAllById(memberships.stream().map(WorkspaceMember::getWorkspaceId).toList()).stream()
                .collect(Collectors.toMap(Workspace::getId, Workspace::getName));
    }

    private static AccountWorkspace workspaceOf(WorkspaceMember member, Map<UUID, String> names) {
        return new AccountWorkspace(member.getWorkspaceId(), names.get(member.getWorkspaceId()));
    }
}
