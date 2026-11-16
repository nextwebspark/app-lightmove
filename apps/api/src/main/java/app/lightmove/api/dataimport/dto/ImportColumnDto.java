package app.lightmove.api.dataimport.dto;

import java.util.List;

/** One column as the mapping step shows it. The sample values go back to the browser only, never the model. */
public record ImportColumnDto(
        int index,
        String header,
        String valueShape,
        List<String> sampleValues,
        ProposedColumnMappingDto mapping,
        /** Distinct LinkedIn profile and company links in the column, counted here: what enriching it would read. */
        int linkedinProfileCount,
        int linkedinCompanyCount
) {}
