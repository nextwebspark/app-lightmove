/**
 * Chrome rasterises an SVG favicon once and never re-runs its `prefers-color-scheme` rule, so a scheme
 * flip would leave the old ink in the tab until a reload. A new URL makes it fetch and draw again.
 */
export function redrawFaviconOnColorSchemeChange(): void {
  const icon = document.querySelector<HTMLLinkElement>('link[rel="icon"][type="image/svg+xml"]');
  const darkScheme = window.matchMedia?.("(prefers-color-scheme: dark)");
  if (!icon || !darkScheme) {
    return;
  }
  darkScheme.addEventListener("change", (event) => {
    icon.href = `/favicon.svg?scheme=${event.matches ? "dark" : "light"}`;
  });
}
