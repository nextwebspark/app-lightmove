package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.LawfulBasis;

/**
 * The firm as controller of its candidates (#923): named in every privacy notice, its contact taking the replies, its
 * own notice linked. Nothing set is every field null and legitimate interest as the default basis.
 */
public record CandidatePrivacySettings(
        String controllerName,
        String privacyContactEmail,
        String noticeUrl,
        LawfulBasis defaultLawfulBasis
) {

    public static final CandidatePrivacySettings NOT_SET_UP =
            new CandidatePrivacySettings(null, null, null, LawfulBasis.LEGITIMATE_INTEREST);

    /** A notice names the firm, says where to write and links its own notice; without all three none is sent. */
    public boolean isReadyForNotices() {
        return controllerName != null && privacyContactEmail != null && noticeUrl != null;
    }
}
