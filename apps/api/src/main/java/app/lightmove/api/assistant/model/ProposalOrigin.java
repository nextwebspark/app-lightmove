package app.lightmove.api.assistant.model;

/**
 * Where a proposed company's data came from, and therefore which door it files through.
 *
 * <p>Not a presentation detail: the two doors have opposite trust models. A {@link #UNIVERSE} row is
 * an id the server resolved every field of, and files in one statement through
 * {@code TriageCompanyService.addSelected}. The other two carry their own fields — nobody resolved
 * them — and file through {@code capture}, which is documented as exactly that mirror image.
 *
 * <p><b>Never the model's to choose.</b> It follows from which tool produced the row, so a guess
 * cannot be dressed up as a universe hit. {@code Assistant.dc.html} badges all three on every row —
 * green held, amber bought, grey open web — because a consultant deciding whether to file a company
 * is entitled to know whether anybody checked it.
 */
public enum ProposalOrigin {

    /** Our own Apollo universe: free, authoritative, and the only one this iteration can produce. */
    UNIVERSE,

    /** A vendor looked the company up and we paid for the answer. Arrives with the research tools. */
    RESEARCHED,

    /** Found on the open web and unverified — discovery only. Arrives with the grounded search. */
    WEB
}
