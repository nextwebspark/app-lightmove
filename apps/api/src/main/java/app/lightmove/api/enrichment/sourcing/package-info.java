/**
 * <b>Find executives</b> — one press on the In-universe page that turns the brief into title words,
 * searches Bright Data's people dataset once per company, has the model pick the best fits, and files
 * them through {@code candidate}'s own door. The run is a row ({@code app_lm_executive_sourcing_run},
 * V86) so the screen can poll it, read it back after a reload, and be refused a second one.
 */
package app.lightmove.api.enrichment.sourcing;
