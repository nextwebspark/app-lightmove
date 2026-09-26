package app.lightmove.api.dataimport.service;

import app.lightmove.api.customcolumn.dto.CustomColumnDto;
import app.lightmove.api.dataimport.constant.ImportTargetField;

/** What one sheet column turned out to be: a built-in field, or a custom column of the mandate's. */
sealed interface ResolvedColumn permits ResolvedColumn.BuiltInColumn, ResolvedColumn.DefinedCustomColumn {

    record BuiltInColumn(ImportTargetField field) implements ResolvedColumn {}

    record DefinedCustomColumn(CustomColumnDto column) implements ResolvedColumn {}
}
