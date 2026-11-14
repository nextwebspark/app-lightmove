package app.lightmove.api.candidate.dto;

import java.time.Instant;

/**
 * The drawer's Privacy row (#923). {@code lawfulBasis} is the basis in force, a {@code LawfulBasis} wire token, and
 * {@code basisIsDefault} says it is the workspace's rather than one recorded on the person. {@code noticeChannel} is
 * a {@code PrivacyNoticeChannel} wire token; both notice fields are null for someone never told. {@code optedOutAt} is
 * when they last stopped emails from an outreach email (#924), while do not contact still stands.
 */
public record PersonPrivacyResponse(
        String lawfulBasis,
        boolean basisIsDefault,
        String basisSetByName,
        Instant basisSetAt,
        Instant notifiedAt,
        String noticeChannel,
        Instant optedOutAt
) {}
