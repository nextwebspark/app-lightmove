/**
 * The HTTP contract for the Strategy screen and the company pickers: a mandate's saved filter, the
 * searches saved against it, the filtered page of companies, and the sidebar's facet counts.
 *
 * <p>Two gates meet here, and the split is deliberate. The <b>facets and name suggestions</b> describe
 * the shared universe, so they are workspace-level and caller-parameterised. <b>Anything derived from
 * a mandate's stored filter</b> — the company page, the off-limits list, the saved searches — is team
 * content behind the project seat, because a mandate's chosen scope reveals its thinking.
 *
 * <p>All responses are derived records, never entities.
 */
package app.lightmove.api.strategy.dto;
