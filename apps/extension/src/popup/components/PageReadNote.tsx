import { workspaceOrigin } from "../../workspaceOrigin";
import type { PageReadError } from "../hooks/useActivePage";

/**
 * Why the page could not be read. `LINKEDIN_ONLY` is not a failure but a boundary — the plugin
 * reads LinkedIn only, for now — so it renders as an even-toned pointer to the app, with a button
 * to the place manual adds live: the selected mandate's Companies page, or the projects list.
 */
export function PageReadNote({
  error,
  selectedProjectId,
}: {
  error: PageReadError | null;
  selectedProjectId: string | null;
}) {
  if (!error) {
    return null;
  }

  if (error.code === "LINKEDIN_ONLY") {
    const url = selectedProjectId
      ? `${workspaceOrigin}/projects/${selectedProjectId}/companies/universe`
      : `${workspaceOrigin}/`;
    return (
      <div className="mb-3.5 rounded-lg border border-line-soft bg-panel2 px-2.5 py-2 text-[11.5px] leading-[1.5] text-text2">
        <p>{error.message}</p>
        <button
          type="button"
          onClick={() => void chrome.tabs.create({ url })}
          className="mt-2 rounded-md bg-amber-btn px-2.5 py-1.5 text-[11.5px] font-semibold text-on-amber"
        >
          Open LightMove
        </button>
      </div>
    );
  }

  return (
    <div
      role="alert"
      className="mb-3.5 rounded-lg border border-line-soft bg-red-dim px-2.5 py-2 text-[11.5px] leading-[1.5] text-red"
    >
      <p>{error.message}</p>
    </div>
  );
}
