package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.notetaker.constant.MeetingRecorderKind;
import app.lightmove.api.notetaker.constant.NotetakerEndReason;
import app.lightmove.api.notetaker.constant.NotetakerPlatform;
import app.lightmove.api.notetaker.model.LaunchedBot;
import app.lightmove.api.notetaker.model.NotetakerLaunch;
import app.lightmove.api.notetaker.model.OfferedPlatforms;
import app.lightmove.api.outreach.service.ZoomService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Our own bot first and Recall's behind it, in the shape of {@code PeopleSearchChain}: a call ours joins goes to ours,
 * and to Recall only when ours could not be started and {@link FallbackPolicy} says so; a call only Recall joins (Zoom)
 * goes straight to it, as does any call on a platform whose canary failed (#956). Recall is a third party that receives
 * the call's audio, so it stands behind ours only in a workspace whose admin turned it on. Every fallback is logged and
 * counted by platform and reason — the measure of how much ours can be trusted.
 */
@Component
@Slf4j
public class ChainedMeetingRecorder {

    static final String FALLBACKS = "notetaker.fallbacks";

    private final MeetingRecorder own;
    private final RecallRecorder backup;
    private final OfferedPlatforms ownPlatforms;
    private final FallbackPolicy policy;
    private final NotetakerSwitches switches;
    private final NotetakerPlatformHealth health;
    private final MeterRegistry meters;

    ChainedMeetingRecorder(MeetingRecorder own, RecallBotApi recall, ZoomService zoom, LightMoveProperties properties,
                           NotetakerSwitches switches, NotetakerPlatformHealth health, MeterRegistry meters) {
        this.own = own;
        this.backup = new RecallRecorder(recall, zoom);
        this.ownPlatforms = OfferedPlatforms.parse(properties.notetaker().platforms());
        this.policy = FallbackPolicy.of(properties.notetaker());
        this.switches = switches;
        this.health = health;
        this.meters = meters;
    }

    /** Never retried by the caller: a second bot in an executive's call is worse than none. */
    public LaunchedBot launch(NotetakerLaunch launch) {
        boolean backupAllowed = backupAllowedIn(launch.workspaceId());
        // The canary says ours cannot get into this platform's calls today: the backup takes them, where it may.
        if (ownPlatforms.offers(launch.platform()) && backupAllowed && health.routesAround(launch.platform())) {
            return launchBackup(launch, NotetakerEndReason.PLATFORM_UNHEALTHY);
        }
        if (ownPlatforms.offers(launch.platform())) {
            try {
                return new LaunchedBot(MeetingRecorderKind.OWN, own.launch(launch), null);
            } catch (RuntimeException failed) {
                if (!backupAllowed || !policy.fallsBack(NotetakerEndReason.LAUNCH_FAILED)) {
                    throw new BotNotSent(NotetakerEndReason.LAUNCH_FAILED, failed);
                }
                log.warn("Notetaker run {}: our bot could not be started; sending the backup", launch.recordingId(),
                        failed);
                return launchBackup(launch, NotetakerEndReason.LAUNCH_FAILED);
            }
        }
        if (backupAllowed) {
            return launchBackup(launch, null);
        }
        throw new BotNotSent(NotetakerEndReason.LAUNCH_FAILED, null);
    }

    /** Sends the backup's bot, {@code reason} being why ours is not answering (null: a call only the backup joins). */
    public LaunchedBot launchBackup(NotetakerLaunch launch, NotetakerEndReason reason) {
        if (reason != null) {
            count(launch.platform(), reason);
        }
        try {
            return new LaunchedBot(MeetingRecorderKind.RECALL, backup.launch(launch), reason);
        } catch (BotNotSent refused) {
            throw refused;
        } catch (RuntimeException failed) {
            throw new BotNotSent(reason == null ? NotetakerEndReason.LAUNCH_FAILED : reason, failed);
        }
    }

    /** Whether a failure of ours, with this reason, is followed by the backup's bot in this workspace. */
    public boolean fallsBack(NotetakerEndReason reason, UUID workspaceId) {
        return policy.fallsBack(reason) && backupAllowedIn(workspaceId);
    }

    /** Whether this deployment has a backup recorder a workspace could turn on. */
    public boolean backupOffered() {
        return backup.isOffered();
    }

    public boolean backupAllowedIn(UUID workspaceId) {
        return backup.isOffered() && switches.backupAllowed(workspaceId);
    }

    /** Of these workspaces, the ones the backup stands behind ours in; one query for the scheduler's whole pass. */
    public Set<UUID> backupAllowedAmong(Collection<UUID> workspaceIds) {
        return backup.isOffered() ? switches.workspacesWithBackup(workspaceIds) : Set.of();
    }

    /** Whether some bot joins this platform's calls: ours on its own platforms, the backup's on any where allowed. */
    public boolean joins(NotetakerPlatform platform, boolean backupAllowed) {
        return platform != null && (ownPlatforms.offers(platform) || backupAllowed);
    }

    /** The platforms some bot joins in this workspace; Zoom only through the backup, on the consultant's own Zoom. */
    public Set<NotetakerPlatform> joinedPlatforms(UUID workspaceId) {
        Set<NotetakerPlatform> joined = EnumSet.noneOf(NotetakerPlatform.class);
        joined.addAll(ownPlatforms.platforms());
        if (backupAllowedIn(workspaceId)) {
            joined.addAll(EnumSet.allOf(NotetakerPlatform.class));
        }
        return joined;
    }

    public void requestLeave(MeetingRecorderKind recorder, String externalId) {
        if (recorder == MeetingRecorderKind.RECALL) {
            backup.requestLeave(externalId);
        } else {
            own.requestLeave(externalId);
        }
    }

    private void count(NotetakerPlatform platform, NotetakerEndReason reason) {
        log.info("Notetaker fallback to the backup recorder on {} after {}", platform, reason);
        meters.counter(FALLBACKS, "platform", String.valueOf(platform), "reason", reason.name()).increment();
    }
}
