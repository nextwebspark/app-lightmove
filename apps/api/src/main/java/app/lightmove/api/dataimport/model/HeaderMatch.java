package app.lightmove.api.dataimport.model;

import app.lightmove.api.dataimport.constant.ImportTargetField;

/**
 * A header matched to a field, and how sure the match is.
 *
 * <p>{@code certain} means the header <i>is</i> a spelling this application knows — an entry in the
 * synonym table. A fuzzy token-overlap hit is a good guess and not the same thing, and the difference
 * decides whether the model is asked at all.
 */
public record HeaderMatch(ImportTargetField field, boolean certain) {

    public static HeaderMatch certain(ImportTargetField field) {
        return new HeaderMatch(field, true);
    }

    public static HeaderMatch likely(ImportTargetField field) {
        return new HeaderMatch(field, false);
    }
}
