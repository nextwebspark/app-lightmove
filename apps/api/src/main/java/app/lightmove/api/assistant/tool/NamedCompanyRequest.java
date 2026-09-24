package app.lightmove.api.assistant.tool;

/**
 * One company the model names from memory. {@code localOperator} is the company that runs a global
 * brand in the country — Majid Al Futtaim for Carrefour in the UAE — where the model knows of one,
 * since that is where the local executives work.
 */
public record NamedCompanyRequest(String name, String localOperator) {}
