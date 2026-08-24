import { SectionLabel } from "../components/PopupChrome";

/**
 * The Person tab, honestly.
 *
 * People do not exist in the product yet — there is no candidates table, no pipeline, no outreach — so
 * there is nowhere for a captured person to land. Rendering the fields anyway would produce a form
 * that looks like it saves and does not, which is worse than a tab that says what it is waiting for.
 */
export function PersonCaptureComingSoonScreen() {
  return (
    <div className="flex flex-1 flex-col items-center justify-center px-6 text-center">
      <SectionLabel>Coming next</SectionLabel>
      <p className="mt-3 text-[13px] font-semibold">Person capture is not ready yet</p>
      <p className="mt-2 text-[12px] leading-[1.6] text-text2">
        A mandate has nowhere to put a person yet — the candidates and pipeline tables are still being
        built. Company capture works today; switch to the Company tab.
      </p>
    </div>
  );
}
