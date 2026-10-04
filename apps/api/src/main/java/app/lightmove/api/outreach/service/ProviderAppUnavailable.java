package app.lightmove.api.outreach.service;

/**
 * The workspace's OAuth app, not the consultant's grant, is what failed: its secret expired or was rotated
 * ({@code invalid_client}), it lost a permission ({@code unauthorized_client}), or none resolves at all. Nothing
 * was sent and the mailbox is fine, so it is never read as access withdrawn — an admin fixing the app in
 * Settings → Integrations brings every mailbox back at once, and reconnecting through a broken app could not.
 */
public class ProviderAppUnavailable extends RuntimeException {

    public ProviderAppUnavailable(String why) {
        super(why);
    }
}
