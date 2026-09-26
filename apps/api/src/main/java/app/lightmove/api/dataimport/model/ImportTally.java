package app.lightmove.api.dataimport.model;

import app.lightmove.api.dataimport.dto.ImportRowErrorDto;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One commit's running totals, mutable by design. Errors cap at {@link #MAX_REPORTED_ERRORS}; the
 * count keeps growing past it.
 */
public final class ImportTally {

    private static final int MAX_REPORTED_ERRORS = 100;

    private final List<ImportRowErrorDto> rowErrors = new ArrayList<>();
    private final Set<String> customColumnsCreated = new LinkedHashSet<>();

    private int rowsRead;
    private int companiesCreated;
    private int companiesUpdated;
    private int companiesSkipped;
    private int candidatesCreated;
    private int candidatesUpdated;
    private int failedRows;

    public void countRow() {
        rowsRead++;
    }

    public void companyCreated() {
        companiesCreated++;
    }

    public void companyUpdated() {
        companiesUpdated++;
    }

    /** A market company whose facts were left alone. */
    public void companySkipped() {
        companiesSkipped++;
    }

    public void candidateCreated() {
        candidatesCreated++;
    }

    public void candidateUpdated() {
        candidatesUpdated++;
    }

    public void customColumnCreated(String label) {
        customColumnsCreated.add(label);
    }

    public void rowFailed(int rowNumber, String message) {
        failedRows++;
        if (rowErrors.size() < MAX_REPORTED_ERRORS) {
            rowErrors.add(new ImportRowErrorDto(rowNumber, message));
        }
    }

    public int rowsRead() {
        return rowsRead;
    }

    public int companiesCreated() {
        return companiesCreated;
    }

    public int companiesUpdated() {
        return companiesUpdated;
    }

    public int companiesSkipped() {
        return companiesSkipped;
    }

    public int candidatesCreated() {
        return candidatesCreated;
    }

    public int candidatesUpdated() {
        return candidatesUpdated;
    }

    public int failedRows() {
        return failedRows;
    }

    public List<String> customColumnsCreated() {
        return List.copyOf(customColumnsCreated);
    }

    public List<ImportRowErrorDto> rowErrors() {
        return List.copyOf(rowErrors);
    }
}
