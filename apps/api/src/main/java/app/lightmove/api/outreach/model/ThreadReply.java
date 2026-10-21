package app.lightmove.api.outreach.model;

import app.lightmove.api.outreach.constant.MailboxGatewayKind;

/** Where a consultant's email answers: the thread, the gateway that made it, and the message in it it replies to. */
public record ThreadReply(String threadId, MailboxGatewayKind gateway, String messageId, String subject) {}
