/**
 * The toolbar icon follows the browser's light/dark setting, like the workspace's favicon: black ink on
 * a light toolbar, white on a dark one. A service worker has no `matchMedia`, so an offscreen page
 * watches the setting and reports it back as `colorSchemeChanged`.
 */
export interface ToolbarIconDeps {
  hasOffscreenDocument: () => Promise<boolean>;
  createOffscreenDocument: (parameters: chrome.offscreen.CreateParameters) => Promise<void>;
  setIcon: (details: chrome.action.TabIconDetails) => Promise<void>;
}

export const COLOR_SCHEME_DOCUMENT_PATH = "color-scheme.html";

export function toolbarIconPaths(isDarkScheme: boolean): Record<16 | 32, string> {
  const theme = isDarkScheme ? "dark" : "light";
  return { 16: `icons/uncava-app-icon-${theme}-16.png`, 32: `icons/uncava-app-icon-${theme}-32.png` };
}

export async function applyToolbarIcon(deps: ToolbarIconDeps, isDarkScheme: boolean): Promise<void> {
  await deps.setIcon({ path: toolbarIconPaths(isDarkScheme) }).catch(() => undefined);
}

/**
 * Opens the watcher unless one is already running. Chrome allows a single offscreen document, and a
 * worker restart racing an earlier create rejects the second one, so every failure here leaves the
 * manifest's black icon standing rather than failing the worker.
 */
export async function watchColorScheme(deps: ToolbarIconDeps): Promise<void> {
  try {
    if (await deps.hasOffscreenDocument()) {
      return;
    }
    await deps.createOffscreenDocument({
      url: COLOR_SCHEME_DOCUMENT_PATH,
      reasons: ["MATCH_MEDIA" as chrome.offscreen.Reason],
      justification: "Match the toolbar icon's ink to the browser's light or dark setting.",
    });
  } catch {
    return;
  }
}
