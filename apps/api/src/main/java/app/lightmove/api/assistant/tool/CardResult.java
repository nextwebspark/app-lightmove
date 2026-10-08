package app.lightmove.api.assistant.tool;

/**
 * What {@code proposeCompanies} tells the model about the card it made: how many companies it holds,
 * how many of those the mandate had already filed, and how many asked for were left off.
 */
public record CardResult(int suggested, int alreadyInMandate, int leftOut) {}
