import { resolve } from "node:path";
import { defineConfig, type UserConfig } from "vite";

/**
 * Builds one classic (non-module) script into the same `dist/` the main build produced.
 *
 * MV3 injects `chrome.scripting` files as *classic* scripts: an `import` in one is a syntax error at
 * injection time and the failure is silent. So each is a separate IIFE pass — IIFE output cannot
 * code-split — and none of them may empty `dist/`.
 */
export function iifeBundle(options: {
  entry: string;
  fileName: string;
  globalName: string;
  /**
   * Appended verbatim as the script's last statement — the completion value of an injected script is
   * what `InjectionResult.result` carries, and an IIFE bundle ends in a `var`, whose value is
   * undefined.
   */
  footer?: string;
}): UserConfig {
  return defineConfig({
    build: {
      outDir: "dist",
      emptyOutDir: false,
      minify: false,
      lib: {
        entry: resolve(import.meta.dirname, options.entry),
        formats: ["iife"],
        name: options.globalName,
        fileName: () => options.fileName,
      },
      rollupOptions: options.footer ? { output: { footer: options.footer } } : {},
    },
  });
}
