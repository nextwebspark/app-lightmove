/**
 * The HTTP contract for a mandate's custom grid columns — the definitions only. A column's
 * <i>values</i> travel on the row that holds them, in the {@code customFields} map on the
 * triage-company and candidate payloads.
 */
@NamedInterface(name = "api", propagate = false)
package app.lightmove.api.customcolumn.dto;

import org.springframework.modulith.NamedInterface;
