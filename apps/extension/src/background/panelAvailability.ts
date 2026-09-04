import { SIDE_PANEL_PATH } from "../buildTargets";
import { isLinkedInPageUrl } from "../content/pageReader/linkedInUrls";

/**
 * Where LightMove Capture offers itself at all.
 *
 * The panel is hidden and the toolbar icon greyed on every tab that is not LinkedIn, rather than
 * opening to explain that it reads LinkedIn only. Chrome hides a disabled panel on tab switch and
 * <b>brings it back by itself</b> when the consultant returns to a tab where it was open, so this
 * costs nothing of the panel following them from profile to profile.
 *
 * It changes no permission and guards nothing: `host_permissions` already covers LinkedIn and the
 * workspace and nothing else, so the extension could never read another site. This is what the
 * toolbar says about that.
 */
export interface PanelAvailabilityDeps {
  queryTabs: (query: chrome.tabs.QueryInfo) => Promise<chrome.tabs.Tab[]>;
  setPanelOptions: (options: chrome.sidePanel.PanelOptions) => Promise<void>;
  enableAction: (tabId: number) => Promise<void>;
  disableAction: (tabId: number) => Promise<void>;
  setActionTitle: (details: { tabId: number; title: string }) => Promise<void>;
}

const AVAILABLE_TITLE = "LightMove Capture";
const UNAVAILABLE_TITLE = "LightMove Capture — open a LinkedIn profile or company page";

/**
 * Applies the rule to one tab.
 *
 * A tab still initialising has no URL to judge, and a new tab is not LinkedIn — so unknown is
 * unavailable. Skipping it instead would leave the manifest's global default standing, which is
 * enabled, and the panel would show on a page it has no business on.
 *
 * Disabling the panel for the tab it is *currently* open in hides it, so following an outbound link
 * away from LinkedIn takes the form with it. That is the same page change that clears the form
 * anyway; there is nothing on screen worth keeping once the consultant has left LinkedIn.
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
