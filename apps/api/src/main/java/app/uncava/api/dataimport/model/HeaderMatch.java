package app.uncava.api.dataimport.model;

import app.uncava.api.dataimport.constant.ImportTargetField;

/** {@code certain} means a synonym-table spelling, not a fuzzy hit — which decides whether the model is asked. */
public record HeaderMatch(ImportTargetField field, boolean certain) {

    public static HeaderMatch certain(ImportTargetField field) {
        return new HeaderMatch(field, true);
    }

    public static HeaderMatch likely(ImportTargetField field) {
        return new HeaderMatch(field, false);
    }
}
