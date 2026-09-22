/**
 * <b>Company discovery — the market we do not hold.</b> Strategy's AI Research: a consultant's
 * free-text market question, answered by a grounded web search, resolved against what we already
 * have, and handed back as rows a person ticks and files.
 *
 * <p><b>This package reads nothing of its own.</b> The universe comes from {@code strategy}, the
 * mandate's already-filed companies from {@code triagecompany}, and a vendor's record of a LinkedIn
 * page from {@code enrichment} — three seams, none depending back. A composer, like
 * {@code dataexport} and {@code report}; deliberately not a subpackage of {@code strategy}, which
 * depends on neither of the other two and documents that direction as one-way.
 *
 * <p><b>The rule the whole feature is built around: the model may propose identifiers and a reason.
 * Every figure and every classification comes from a record.</b> A name, a LinkedIn URL and a
 * homepage are addresses — they are checkable, and they are only ever used as lookup keys. A
 * headcount, a revenue, a country or an industry is a claim, and a claim the model made would reach
 * a client-facing report as though somebody had checked it. So a company neither the universe nor a
 * vendor carries is answered with its name and nothing else, and the grid draws the empty cells.
 *
 * <p>It writes nothing. An answer is filed through {@code POST /triage/bulk} for the rows the
 * universe carries and {@code POST /triage/capture} for the rows it does not — the doors that
 * already exist, with their own scope checks and their own audit events.
 */
package app.lightmove.api.companydiscovery;
