package app.lightmove.api.outreach.model;

/** One Recall webhook delivery as it arrived: Svix's three signing headers and the raw body they sign. */
public record RecallWebhookDelivery(String id, String timestamp, String signature, byte[] body) {}
