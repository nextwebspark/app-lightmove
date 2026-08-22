/**
 * What an accordion shows when `/companies/facets` was refused rather than merely slow.
 *
 * <p>A project CLIENT seat holds WORK_VIEW, so it reads the mandate's filter and its results, but
 * the facet counts are gated PROJECT_BROWSE and 403 for a pure client representative. Rendering the
 * loading skeleton for that case left the rail pulsing forever beside a table that had loaded fine.
 */
export function FacetsUnavailable() {
  return (
    <p className="font-sans text-[12px] leading-relaxed text-text3">
      These counts are not available to you. The results beside this rail are unaffected.
    </p>
  );
}
