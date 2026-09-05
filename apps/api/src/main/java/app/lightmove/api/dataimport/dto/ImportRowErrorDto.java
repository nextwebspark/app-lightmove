package app.lightmove.api.dataimport.dto;

/**
 * One row the import could not take, and why.
 *
 * <p>{@code rowNumber} counts from the file's own first data row as a person reading it in Excel
 * would — a row number they cannot find in the file is not a row number.
 */
public record ImportRowErrorDto(
        int rowNumber,
        String message
) {}
