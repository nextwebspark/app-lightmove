package app.lightmove.api.outreach.constant;

/** How a conversation line travelled. Only email is sent or read so far; the rest are reserved for later doors. */
public enum MessageChannel {
    EMAIL,
    WHATSAPP,
    SMS,
    CALL
}
