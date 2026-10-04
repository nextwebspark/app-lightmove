package app.lightmove.api.core.security.dto;

import app.lightmove.api.core.security.constant.DeviceKind;
import java.time.Instant;
import java.util.UUID;

/**
 * One live session in Settings → Active sessions.
 *
 * @param id           the refresh-token <i>family</i> id — what survives rotation, so what Revoke names
 * @param ipAddress    shown in place of a city: an address the owner does not recognise is the signal
 * @param lastActiveAt the last refresh, trailing real activity by up to one access-token lifetime
 */
public record ActiveSessionResponse(
        UUID id,
        String device,
        DeviceKind deviceKind,
        String ipAddress,
        Instant lastActiveAt,
        boolean current
) {}
