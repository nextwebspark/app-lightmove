/**
 * The HTTP contract for importing a spreadsheet into a mandate's Companies grid: previewing the file
 * with a proposed column mapping, and committing the mapping a person confirmed.
 *
 * <p>Two calls carrying the same file, and deliberately no import session between them. The browser
 * already holds the file it just uploaded, so re-posting it with the confirmed mapping costs one
 * parse and saves a staging table, an expiry policy, and a sweeper for the imports nobody came back
 * to finish.
 */
package app.lightmove.api.dataimport.dto;
