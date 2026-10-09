/**
 * <b>Contact lookup</b> — the Find email / Find phone buttons, one channel per press. Answers on the
 * request thread so a paid press can report its own failure ("no credits left"), and writes through
 * {@code CandidateService.applyFoundEmails} / {@code applyFoundPhones}. {@code lightmove.enrichment.contactout}
 * sits beside {@code lightmove.enrichment.provider} and is never selected by it: its own account and bill.
 * A press is charged to the workspace's contact credits through {@code billing}'s {@code CreditLedger}, and only
 * for what was found.
 */
package app.lightmove.api.enrichment.contact;
