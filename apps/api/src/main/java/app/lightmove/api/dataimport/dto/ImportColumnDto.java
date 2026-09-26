package app.lightmove.api.dataimport.dto;

import java.util.List;

/** One column as the mapping step shows it. The sample values go back to the browser only, never the model. */
public record ImportColumnDto(
        int index,
        String header,
        String valueShape,
        List<String> sampleValues,
        ProposedColumnMappingDto mapping
) {}
