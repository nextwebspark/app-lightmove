package app.lightmove.api.outreach.dto;

/** The tab row's two counts, for the scope asked: people needing action, and people with an email still to go. */
public record ConversationCountsResponse(long needs, long scheduled) {}
