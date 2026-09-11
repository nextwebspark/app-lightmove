package app.lightmove.api.dataimport.model;

import java.util.List;

/**
 * One column of an uploaded sheet: the header as written, where it sits, and what its values look
 * like.
 *
 * <p>{@link #valueShape} is computed locally and is the only thing about a column's <i>contents</i>
 * that ever reaches the model — a header alone is often ambiguous ("Contact" could be an email, a
 * phone or a person) and the shape settles it without a candidate's details leaving this process.
 * {@link #sampleValues} do <b>not</b> go to the model; they travel back to the browser so the person
 * confirming the mapping can see what is in the column.
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

    /** What a column's values look like. Deliberately coarse: it disambiguates a header, not the data. */
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
