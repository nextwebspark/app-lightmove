/**
 * <b>Talent map — a mandate's mapping, as points on a globe.</b> The Companies screen's map view
 * reads one thing: every company of a stage and every executive mapped at them, each with a point
 * where one could be resolved.
 *
 * <p><b>This package composes and owns nothing.</b> Companies come from {@code triagecompany}'s
 * {@code listAllOfStage}, people from {@code candidate}'s {@code listAllOfProject}, points from
 * {@code geocoding} — three seams, each answering in its own DTO, and none of the three depends back.
 * {@code triagecompany} still never learns that people exist: the pairing of a person with a company
 * happens here, in the response, exactly as the grid pairs them in the browser.
 *
 * <p>The read is unpaged on purpose and capped instead ({@code lightmove.talent-map.*}); the totals
 * travel with it so a mandate past a cap is told. It is also not transactional: the geocoding it
 * triggers may call a vendor, and a vendor call belongs in no transaction.
 */
package app.lightmove.api.talentmap;
