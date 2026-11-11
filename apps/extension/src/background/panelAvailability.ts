import { SIDE_PANEL_PATH } from "../buildTargets";
import { isLinkedInPageUrl } from "../content/pageReader/linkedInUrls";

/**
 * Where UNCAVA Capture offers itself: panel hidden and icon greyed on every non-LinkedIn tab (Chrome restores a
 * disabled panel on return by itself). UX only — `host_permissions` is what limits what the extension can read.
 */
export interface PanelAvailabilityDeps {
  queryTabs: (query: chrome.tabs.QueryInfo) => Promise<chrome.tabs.Tab[]>;
  setPanelOptions: (options: chrome.sidePanel.PanelOptions) => Promise<void>;
  enableAction: (tabId: number) => Promise<void>;
  disableAction: (tabId: number) => Promise<void>;
  setActionTitle: (details: { tabId: number; title: string }) => Promise<void>;
}

const AVAILABLE_TITLE = "UNCAVA Capture";
const UNAVAILABLE_TITLE = "UNCAVA Capture — open a LinkedIn profile or company page";

/**
 * Applies the rule to one tab. A tab with no URL yet counts as unavailable — skipping it would leave the
 * manifest's enabled default standing. Leaving LinkedIn in the open tab hides the panel, which that page change
 * clears anyway.
 */
export async function applyPanelAvailability(
  deps: PanelAvailabilityDeps,
  tab: { id?: number; url?: string },
): Promise<void> {
  if (!tab.id) {
    return;
  }
  const tabId = tab.id;
  const isAvailable = isLinkedInPageUrl(tab.url);
  // Every call races a tab the consultant may have just closed, which rejects. Nothing here is worth
  // failing the worker over.
  const ignoringClosedTabs = () => undefined;

  await Promise.all([
    // The path goes with every enable. Leaving it to the manifest's `default_path` does not work once
    // a tab has been given options of its own: the tab comes back enabled and pointing at nothing, so
    // the icon lights up on LinkedIn and clicking it opens no panel.
    deps
      .setPanelOptions(isAvailable ? { tabId, enabled: true, path: SIDE_PANEL_PATH } : { tabId, enabled: false })
      .catch(ignoringClosedTabs),
    // Without this the icon stays lit on every site and its click does nothing at all — a disabled
    // panel refuses silently, which reads as a broken extension rather than one that is not for
    // this page.
    (isAvailable ? deps.enableAction(tabId) : deps.disableAction(tabId)).catch(ignoringClosedTabs),
    deps
      .setActionTitle({ tabId, title: isAvailable ? AVAILABLE_TITLE : UNAVAILABLE_TITLE })
      .catch(ignoringClosedTabs),
  ]);
}

/**
 * Turns off the panel that is not about any tab.
 *
 * Belt and braces beside dropping `side_panel.default_path` from the manifest: a global panel is not
 * overridden by per-tab options, so if one ever exists the site scoping below is decoration.
 */
export async function disableGlobalPanel(deps: PanelAvailabilityDeps): Promise<void> {
  await deps.setPanelOptions({ enabled: false }).catch(() => undefined);
}

/**
 * Every tab open right now.
 *
 * `tabs.onUpdated` only fires on a change, so a tab that was already open when the extension was
 * installed — or when the browser restarted — would never be judged, and would keep the manifest's
 * enabled-everywhere default.
 */
export async function applyPanelAvailabilityToAllTabs(deps: PanelAvailabilityDeps): Promise<void> {
  const tabs = await deps.queryTabs({});
  await Promise.all(tabs.map((tab) => applyPanelAvailability(deps, tab)));
}
