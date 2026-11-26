package app.lightmove.api.candidate.constant;

import app.lightmove.api.core.audit.constant.ProjectEventType;

/** The door a person's objection to contact came through (B16), stored as {@code do_not_contact_source}. */
public enum ContactStopKind {

    OBJECTED_ON_PRIVACY_PAGE("Objected through their privacy page", PersonActivityKind.PRIVACY_OBJECTED,
            ProjectEventType.PERSON_OBJECTED),
    OPTED_OUT_BY_EMAIL("Opted out by email", PersonActivityKind.PRIVACY_OPTED_OUT, ProjectEventType.PERSON_OPTED_OUT),
    RECORDED_BY_TEAM("Objected when contacted, recorded by the team", PersonActivityKind.OBJECTION_RECORDED,
            ProjectEventType.PERSON_OBJECTION_RECORDED);

    private final String doNotContactReason;
    private final PersonActivityKind activityKind;
    private final ProjectEventType auditType;

    ContactStopKind(String doNotContactReason, PersonActivityKind activityKind, ProjectEventType auditType) {
        this.doNotContactReason = doNotContactReason;
        this.activityKind = activityKind;
        this.auditType = auditType;
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
