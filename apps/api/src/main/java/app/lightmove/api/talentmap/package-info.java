/**
 * <b>Talent map</b> — every company of a stage and every executive mapped at them, as points on a
 * globe. It composes and owns nothing: companies from {@code triagecompany}, people from
 * {@code candidate}, points from {@code geocoding}, paired here so {@code triagecompany} never learns
 * people exist. Unpaged and capped ({@code lightmove.talent-map.*}), with totals so a capped mandate is told.
 */
package app.lightmove.api.talentmap;
