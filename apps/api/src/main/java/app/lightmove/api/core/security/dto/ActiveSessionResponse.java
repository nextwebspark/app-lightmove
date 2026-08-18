package app.lightmove.api.core.security.dto;

import app.lightmove.api.core.security.constant.DeviceKind;
import java.time.Instant;
import java.util.UUID;

/**
 * One live session in Settings → Active sessions.
 *
 * @param id           the refresh-token <i>family</i> id, not a row id. A session's rows are replaced
 *                     on every rotation; the family is what survives, so it is what a Revoke names.
 * @param ipAddress    shown in place of a city: we hold no geo data, and an address the owner does not
 *                     recognise is the actual signal that a session is not theirs.
 * @param lastActiveAt when this session last exchanged its refresh token, which trails real activity
 *                     by up to one access-token lifetime.
 */
public record ActiveSessionResponse(
        UUID id,
        String device,
        DeviceKind deviceKind,
        String ipAddress,
        Instant lastActiveAt,
        boolean current
) {}
