import { resolve } from "node:path";
import { defineConfig, type UserConfig } from "vite";

/**
 * Builds one classic (non-module) script into the same `dist/` the main build produced.
 *
 * Manifest V3 injects both content scripts and `chrome.scripting` files as *classic* scripts: an
 * `import` statement in either is a syntax error at injection time, and the failure is silent — the
 * script never runs and the feature simply appears to hang. So each is bundled on its own as an IIFE,
 * which is a separate Rollup pass because IIFE output cannot code-split across entry points.
 *
 * `emptyOutDir: false` throughout: these run after the main build and must not delete it.
 */
export function iifeBundle(options: {
  entry: string;
  fileName: string;
  globalName: string;
  /**
   * Appended verbatim as the script's last statement.
   *
   * How a `chrome.scripting.executeScript({ files })` injection returns a value: the completion value
   * of the injected script becomes `InjectionResult.result`. An IIFE bundle ends in a `var`
   * declaration, whose completion value is `undefined`, so a trailing expression statement is what
   * turns the bundle into something that can answer.
   */
  footer?: string;
}): UserConfig {
  return defineConfig({
    build: {
      outDir: "dist",
      emptyOutDir: false,
      minify: false,
      lib: {
        entry: resolve(options.entry),
        formats: ["iife"],
        name: options.globalName,
        fileName: () => options.fileName,
      },
      rollupOptions: options.footer ? { output: { footer: options.footer } } : {},
    },
  });
}
