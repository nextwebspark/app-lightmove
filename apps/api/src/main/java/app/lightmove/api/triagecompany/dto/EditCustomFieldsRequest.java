package app.lightmove.api.triagecompany.dto;

import java.util.Map;

/** The mandate's own columns for one company, editable whichever door the company came through. */
public record EditCustomFieldsRequest(
        /** Keyed by each column's {@code fieldKey}. A blank value clears that one column. */
        Map<String, String> customFields
) {}
