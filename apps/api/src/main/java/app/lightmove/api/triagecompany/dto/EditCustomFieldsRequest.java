package app.lightmove.api.triagecompany.dto;

import java.util.Map;

/**
 * The mandate's own columns for one company, and nothing else.
 *
 * <p>Separate from {@link EditTriageCompanyRequest} because the two obey different rules: a company's
 * facts belong to whoever supplied them — the market export's are not the mandate's to rewrite —
 * while the columns a mandate added to its own grid are its own whatever door the company came
 * through. One request per rule, rather than one request whose fields are governed by two.
 */
public record EditCustomFieldsRequest(
        /** Keyed by each column's {@code fieldKey}. A blank value clears that one column. */
        Map<String, String> customFields
) {}
