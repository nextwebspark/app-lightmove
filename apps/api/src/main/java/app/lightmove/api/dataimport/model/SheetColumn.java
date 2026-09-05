package app.lightmove.api.dataimport.model;

import java.util.List;

/**
 * One column of an uploaded sheet: the header as written, where it sits, and what its values look
 * like.
 *
 * <p>{@link #valueShape} is computed locally from the column's own cells and is the only thing about
 * a column's <i>contents</i> that the mapping step is given. A header alone is often ambiguous —
 * "Contact" could be an email, a phone number or a person — and the shape settles it without a single
 * candidate's details leaving this process.
 *
 * <p>{@link #sampleValues} feed nothing but the screen. They travel back to the browser that sent
 * them, so the person confirming the mapping can see what is actually in the column they are
 * assigning.
 */
public record SheetColumn(
        int index,
        String header,
        ValueShape valueShape,
        List<String> sampleValues,
        boolean allBlank
) {

    public SheetColumn {
        sampleValues = List.copyOf(sampleValues);
    }

    /**
     * What a column's values look like, judged from the column itself.
     *
     * <p>Deliberately coarse. This exists to disambiguate a header, not to type the data — a finer
     * reading would be guessing, and the person confirming the mapping is a better judge than any
     * rule here.
     */
    public enum ValueShape {
        EMAIL,
        URL,
        NUMBER,
        DATE,
        BOOLEAN,
        SHORT_TEXT,
        LONG_TEXT,
        BLANK
    }
}
