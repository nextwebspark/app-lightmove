package app.lightmove.api.customcolumn.dto;

import java.util.List;

/**
 * A mandate's custom columns, both grids in one read.
 *
 * <p>One response rather than two endpoints because the Companies screen is one screen: a row is a
 * person at a company, so it needs the company columns and the candidate columns to build a single
 * header row, and splitting the read would only make the grid wait twice.
 */
public record CustomColumnsResponse(
        List<CustomColumnDto> columns
) {}
