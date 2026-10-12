package app.lightmove.api.gettingstarted.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.gettingstarted.constant.GettingStartedStep;
import app.lightmove.api.gettingstarted.dto.GettingStartedResponse;
import app.lightmove.api.gettingstarted.dto.GettingStartedStepResponse;
import app.lightmove.api.gettingstarted.model.GettingStartedProgress;
import app.lightmove.api.gettingstarted.repository.GettingStartedProgressRepository;
import app.lightmove.api.outreach.constant.MailboxStatus;
import app.lightmove.api.outreach.dto.MailboxResponse;
import app.lightmove.api.outreach.service.MailboxService;
import app.lightmove.api.position.service.PositionService;
import app.lightmove.api.project.dto.ProjectResponse;
import app.lightmove.api.project.service.ProjectService;
import app.lightmove.api.workspace.service.InvitationService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Steps tick off the workspace's own rows, and once ticked stay ticked. A step's stamp is when this person's card
 * first saw it done — per person, so a colleague joining later stamps work done before they arrived on first view.
 */
@Service
@RequiredArgsConstructor
public class GettingStartedService {

    static final int UNIVERSE_TARGET = 10;

    private final GettingStartedProgressRepository progress;
    private final ProjectService projects;
    private final PositionService positions;
    private final MailboxService mailboxes;
    private final InvitationService invitations;
    private final WorkspaceAccess access;
    private final Clock clock;

    @Transactional
    public GettingStartedResponse view(UUID userId, UUID workspaceId) {
        access.requireStaff(userId, workspaceId);
        Optional<GettingStartedProgress> saved = progress.findByWorkspaceIdAndUserId(workspaceId, userId);
        if (saved.map(GettingStartedProgress::isDismissed).orElse(false)) {
            return new GettingStartedResponse(true, null, List.of());
        }

        List<ProjectResponse> positionsOfWorkspace = projects.list(userId, workspaceId);
        MailboxResponse mailbox = mailboxes.view(userId, workspaceId);
        Set<GettingStartedStep> offered = EnumSet.allOf(GettingStartedStep.class);
        if (!mailbox.offered()) {
            offered.remove(GettingStartedStep.CONNECT_MAILBOX);
        }
        if (!access.holdsAction(userId, workspaceId, WorkspaceAction.MEMBER_INVITE)) {
            offered.remove(GettingStartedStep.INVITE_COLLEAGUE);
        }

        Set<GettingStartedStep> doneNow = doneSteps(workspaceId, positionsOfWorkspace, mailbox, offered);
        Map<GettingStartedStep, Instant> stamped = stampNewlyDone(userId, workspaceId, saved, doneNow);
        List<GettingStartedStep> skipped = saved.map(GettingStartedProgress::getSkippedSteps).orElse(List.of());

        List<GettingStartedStepResponse> steps = new ArrayList<>();
        for (GettingStartedStep step : offered) {
            Instant completedAt = stamped.get(step);
            steps.add(new GettingStartedStepResponse(step, completedAt != null, skipped.contains(step), completedAt));
        }
        return new GettingStartedResponse(false, focusOf(userId, positionsOfWorkspace), steps);
    }

    @Transactional
    public GettingStartedResponse setDismissed(UUID userId, UUID workspaceId, boolean dismissed) {
        GettingStartedProgress row = editableProgressOf(userId, workspaceId);
        if (dismissed) {
            row.dismiss(clock.instant());
        } else {
            row.restore();
        }
        progress.saveAndFlush(row);
        return view(userId, workspaceId);
    }

    @Transactional
    public GettingStartedResponse setSkipped(UUID userId, UUID workspaceId, GettingStartedStep step, boolean skipped) {
        if (step == GettingStartedStep.OPEN_POSITION) {
            // Every later step needs a position, so the first can't be put aside.
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "Opening a position can't be skipped");
        }
        GettingStartedProgress row = editableProgressOf(userId, workspaceId);
        if (skipped) {
            row.skip(step);
        } else {
            row.unskip(step);
        }
        progress.saveAndFlush(row);
        return view(userId, workspaceId);
    }

    private Set<GettingStartedStep> doneSteps(UUID workspaceId, List<ProjectResponse> positionsOfWorkspace,
                                              MailboxResponse mailbox, Set<GettingStartedStep> offered) {
        Set<GettingStartedStep> done = EnumSet.noneOf(GettingStartedStep.class);
        if (!positionsOfWorkspace.isEmpty()) {
            done.add(GettingStartedStep.OPEN_POSITION);
        }
        Set<UUID> ids = positionsOfWorkspace.stream().map(ProjectResponse::id).collect(Collectors.toSet());
        if (!positions.projectsWithBriefWorkedOn(ids).isEmpty()) {
            done.add(GettingStartedStep.WRITE_BRIEF);
        }
        if (positionsOfWorkspace.stream().anyMatch(position -> position.companies() >= UNIVERSE_TARGET)) {
            done.add(GettingStartedStep.FIND_COMPANIES);
        }
        if (positionsOfWorkspace.stream().anyMatch(position -> position.mappedCandidates() > 0)) {
            done.add(GettingStartedStep.MAP_EXECUTIVES);
        }
        if (offered.contains(GettingStartedStep.CONNECT_MAILBOX) && mailbox.connection() != null
                && mailbox.connection().status() == MailboxStatus.ACTIVE) {
            done.add(GettingStartedStep.CONNECT_MAILBOX);
        }
        if (offered.contains(GettingStartedStep.INVITE_COLLEAGUE)
                && (access.activeStaff(workspaceId).size() > 1 || invitations.hasPendingStaffInvitation(workspaceId))) {
            done.add(GettingStartedStep.INVITE_COLLEAGUE);
        }
        return done;
    }

    /** The brief, market and executive steps need a seat, so they lead to the newest position the caller is on. */
    private static UUID focusOf(UUID userId, List<ProjectResponse> positionsOfWorkspace) {
        return positionsOfWorkspace.stream()
                .filter(position -> position.team().stream().anyMatch(seat -> userId.equals(seat.userId())))
                .max(Comparator.comparing(ProjectResponse::createdAt))
                .map(ProjectResponse::id)
                .orElse(null);
    }

    /** Writes only when a step is newly done, so a card with nothing new to stamp is a pure read. */
    private Map<GettingStartedStep, Instant> stampNewlyDone(UUID userId, UUID workspaceId,
                                                           Optional<GettingStartedProgress> saved,
                                                           Set<GettingStartedStep> doneNow) {
        Map<GettingStartedStep, Instant> stamped = saved.map(GettingStartedProgress::getCompletedSteps).orElse(Map.of());
        if (stamped.keySet().containsAll(doneNow)) {
            return stamped;
        }
        Instant now = clock.instant();
        String stamps = doneNow.stream()
                .filter(step -> !stamped.containsKey(step))
                .map(step -> "\"" + step.name() + "\":\"" + now + "\"")
                .collect(Collectors.joining(",", "{", "}"));
        progress.ensureExists(workspaceId, userId);
        progress.stampCompleted(workspaceId, userId, stamps);
        return progress.findByWorkspaceIdAndUserId(workspaceId, userId).orElseThrow().getCompletedSteps();
    }

    private GettingStartedProgress editableProgressOf(UUID userId, UUID workspaceId) {
        access.requireStaff(userId, workspaceId);
        progress.ensureExists(workspaceId, userId);
        return progress.findByWorkspaceIdAndUserId(workspaceId, userId).orElseThrow();
    }
}
