package app.lightmove.api.candidate.constant;

import app.lightmove.api.core.audit.constant.ProjectEventType;

/** The doors through which a person stops all contact (B16): their privacy page, an email's stop link, or the team. */
public enum ContactStopKind {

    OBJECTED_ON_PRIVACY_PAGE(DoNotContactReasons.OBJECTED_THROUGH_PRIVACY_PAGE, PersonActivityKind.PRIVACY_OBJECTED,
            ProjectEventType.PERSON_OBJECTED),
    OPTED_OUT_BY_EMAIL(DoNotContactReasons.OPTED_OUT_BY_EMAIL, PersonActivityKind.PRIVACY_OPTED_OUT,
            ProjectEventType.PERSON_OPTED_OUT),
    /** A consultant read "please stop" in a reply or heard it on a call, and marked the person do not contact. */
    RECORDED_BY_TEAM(DoNotContactReasons.OBJECTED_TO_THE_TEAM, PersonActivityKind.OBJECTION_RECORDED,
            ProjectEventType.PERSON_OBJECTION_RECORDED);

    private final String doNotContactReason;
    private final PersonActivityKind activityKind;
    private final ProjectEventType auditType;

    ContactStopKind(String doNotContactReason, PersonActivityKind activityKind, ProjectEventType auditType) {
        this.doNotContactReason = doNotContactReason;
        this.activityKind = activityKind;
        this.auditType = auditType;
    }

    /** The door a standing objection came through, read off the reason it wrote; null for any other reason. */
    public static ContactStopKind ofReasonOrNull(String doNotContactReason) {
        for (ContactStopKind kind : values()) {
            if (kind.doNotContactReason.equals(doNotContactReason)) {
                return kind;
            }
        }
        return null;
    }

    public String doNotContactReason() {
        return doNotContactReason;
    }

    public PersonActivityKind activityKind() {
        return activityKind;
    }

    public ProjectEventType auditType() {
        return auditType;
    }
}
