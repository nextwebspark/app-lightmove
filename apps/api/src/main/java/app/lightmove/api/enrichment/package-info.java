/**
 * <b>Enrichment</b> — vendor research on what a capture could only point at, run after the commit and
 * off the request thread ({@code contact} answers inline). It never writes a mandate's row: answers go
 * back through the owning feature's public method, and every vendor call goes through
 * {@link app.lightmove.api.core.resilience}. The one table it owns, {@code app_lm_vendor_company} (V64),
 * carries no workspace, project or user — a tenant boundary — and never holds a typed row. Types the
 * owning features consume live with them, so the dependency stays one-way.
 */
package app.lightmove.api.enrichment;
