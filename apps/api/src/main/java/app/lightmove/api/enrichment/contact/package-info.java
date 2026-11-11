/**
 * <b>Contact lookup</b> — Find email / Find phone, one channel per press, answered on the request thread so a paid press
 * reports its own failure. {@code lightmove.enrichment.contactout} is its own account and bill, never selected by
 * {@code lightmove.enrichment.provider}; a press is charged through {@code CreditLedger}, and only for what was found.
 */
package app.lightmove.api.enrichment.contact;
