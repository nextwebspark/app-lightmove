package app.lightmove.api.dataimport.model;

import java.util.List;

/**
 * One column of an uploaded sheet. {@link #valueShape} may reach the model; {@link #sampleValues}
 * go only back to the browser, never to the model.
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

    /** Deliberately coarse: it disambiguates a header, not the data. */
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
