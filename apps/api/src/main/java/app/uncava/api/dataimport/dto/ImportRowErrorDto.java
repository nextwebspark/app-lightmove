package app.uncava.api.dataimport.dto;

/** {@code rowNumber} counts data rows as a person reading the file in Excel would. */
public record ImportRowErrorDto(
        int rowNumber,
        String message
) {}
