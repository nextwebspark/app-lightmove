/**
 * <b>Enrichment</b> — vendor research after the commit, off the request thread; never writes a mandate's row
 * directly and calls vendors through {@link app.lightmove.api.core.resilience}. Its caches (V64, V87) carry no
 * workspace, project or user — a tenant boundary; {@code sourcing}/{@code peoplesearch} write via the owning services.
 */
package app.lightmove.api.enrichment;
