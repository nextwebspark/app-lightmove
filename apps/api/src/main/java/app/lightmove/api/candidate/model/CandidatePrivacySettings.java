package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.CandidatePrivacyMode;
import app.lightmove.api.candidate.constant.ConsentPurpose;
import app.lightmove.api.candidate.constant.LawfulBasis;
import java.util.EnumSet;
import java.util.Set;

/**
 * The firm as controller of its candidates (#923): named in every privacy notice, its contact taking the replies, its
 * own notice linked. Nothing set is every field null and legitimate interest as the default basis. In consent mode
 * (#1267) it also says which purposes it asks about, how long a keep-on-file consent lasts, and how many days before
 * its end to ask again (0 never).
 */
public record CandidatePrivacySettings(
        String controllerName,
        String privacyContactEmail,
        String noticeUrl,
        LawfulBasis defaultLawfulBasis,
        CandidatePrivacyMode mode,
        boolean keepOnFile,
        int keepOnFileMonths,
        boolean shareWithClient,
        boolean marketing,
        int renewalDaysBefore
) {

    public static final CandidatePrivacySettings NOT_SET_UP = new CandidatePrivacySettings(null, null, null,
            LawfulBasis.LEGITIMATE_INTEREST, CandidatePrivacyMode.NOTICES, true, 24, true, false, 30);

    /** A notice names the firm, says where to write and links its own notice; without all three none is sent. */
    public boolean isReadyForNotices() {
        return controllerName != null && privacyContactEmail != null && noticeUrl != null;
    }

    public boolean isReadyForConsent() {
        return isReadyForNotices() && mode == CandidatePrivacyMode.CONSENT && !offeredPurposes().isEmpty();
    }

    public Set<ConsentPurpose> offeredPurposes() {
        Set<ConsentPurpose> offered = EnumSet.noneOf(ConsentPurpose.class);
        if (keepOnFile) {
            offered.add(ConsentPurpose.KEEP_ON_FILE);
        }
        if (shareWithClient) {
            offered.add(ConsentPurpose.SHARE_WITH_CLIENT);
        }
        if (marketing) {
            offered.add(ConsentPurpose.MARKETING);
        }
        return offered;
    }
}
