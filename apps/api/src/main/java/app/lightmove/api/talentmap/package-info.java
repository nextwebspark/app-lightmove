/**
 * <b>Talent map — a mandate's mapping, as points on a globe.</b> Every company of a stage and every
 * executive mapped at them, each with a point where one could be resolved.
 *
 * <p><b>This package composes and owns nothing.</b> Companies come from {@code triagecompany}, people
 * from {@code candidate}, points from {@code geocoding} — three seams, none depending back.
 * {@code triagecompany} still never learns that people exist: the pairing happens here, in the
 * response, exactly as the grid pairs them in the browser.
 *
 * <p>The read is unpaged and capped instead ({@code lightmove.talent-map.*}), with the totals
 * travelling so a mandate past a cap is told.
 */
package app.lightmove.api.talentmap;
