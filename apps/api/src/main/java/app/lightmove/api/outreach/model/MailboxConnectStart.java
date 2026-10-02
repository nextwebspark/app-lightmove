package app.lightmove.api.outreach.model;

import java.net.URI;

/** Where to send the browser to connect a mailbox, and the raw state it must bring back in a cookie. */
public record MailboxConnectStart(URI authorizationUri, String state) {}
