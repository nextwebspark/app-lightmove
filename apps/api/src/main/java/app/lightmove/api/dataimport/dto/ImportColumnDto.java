package app.lightmove.api.dataimport.dto;

import java.util.List;

/**
 * One column of the uploaded file as the mapping step shows it: the header the file carries, a few of
 * its values so the person confirming can see what is in it, and the mapping proposed for it.
 *
 * <p>The sample values travel back to the browser that sent the file and nowhere else — in
 * particular; the mapping step works from the column's shape rather than its contents.
 */
public record ImportColumnDto(
        int index,
        String header,
        String valueShape,
        List<String> sampleValues,
        ProposedColumnMappingDto mapping
) {}
