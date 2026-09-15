import type { ExtensionRequest } from "../background/extensionMessages";

const darkScheme = window.matchMedia("(prefers-color-scheme: dark)");

function reportColorScheme(): void {
  const request: ExtensionRequest = { kind: "colorSchemeChanged", isDarkScheme: darkScheme.matches };
  void chrome.runtime.sendMessage(request).catch(() => undefined);
}

reportColorScheme();
darkScheme.addEventListener("change", reportColorScheme);
