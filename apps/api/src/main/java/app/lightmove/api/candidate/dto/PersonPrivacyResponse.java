package app.lightmove.api.candidate.dto;

import java.time.Instant;

/**
 * The drawer's Privacy row (#923). {@code lawfulBasis} is the basis in force, a {@code LawfulBasis} wire token, and
 * {@code basisIsDefault} says it is the workspace's rather than one recorded on the person. {@code noticeChannel} is
 * a {@code PrivacyNoticeChannel} wire token; both notice fields are null for someone never told. {@code optedOutAt} is
 * when their own stop link set the do not contact that stands now.
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
