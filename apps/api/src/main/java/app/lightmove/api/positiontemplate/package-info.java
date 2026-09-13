/**
 * <b>Position template — the briefs a new mandate is drafted from.</b> The shared library (V42) a
 * platform super admin edits, each firm's own copies of it and templates of its own (V52), the picker a
 * consultant chooses from, and the JSON file both tiers export and import.
 *
 * <p>A firm's template shadows the library's under the same {@code code}, so a library edit reaches
 * every firm that has neither copied nor hidden it.
 *
 * <p>{@link app.lightmove.api.position} drafts briefs through {@code PositionTemplateService.matching}
 * and {@code require}; this package never depends on {@code position}. The vocabulary both speak —
 * employment type, benefit frequency, competency panel and the rest — lives in
 * {@code common.constant} for that reason.
 */
package app.lightmove.api.positiontemplate;
