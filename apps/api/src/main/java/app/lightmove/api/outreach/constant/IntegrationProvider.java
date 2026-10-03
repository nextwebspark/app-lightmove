package app.lightmove.api.outreach.constant;

/** A provider a workspace connects through an OAuth app: mail and calendar at Google and Microsoft, video at Zoom. */
public enum IntegrationProvider {
    GOOGLE("Google Workspace"),
    MICROSOFT("Microsoft 365"),
    ZOOM("Zoom");

    private final String displayName;

    IntegrationProvider(String displayName) {
        this.displayName = displayName;
    }

    /** As Settings → Integrations names it, for whatever a person reads. */
    public String displayName() {
        return displayName;
    }
}
