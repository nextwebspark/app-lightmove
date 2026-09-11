/**
 * The HTTP contract for importing a spreadsheet: previewing the file with a proposed column mapping,
 * and committing the mapping a person confirmed. Both calls carry the file, so there is no import
 * session, staging table or expiry policy between them.
 */
package app.lightmove.api.dataimport.dto;
