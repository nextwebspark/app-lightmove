package app.uncava.api.outreach.dto;

/** Where the SPA points the connect popup: the mail service's hosted sign-in. */
public record MailboxConnectResponse(String authorizationUrl) {}
