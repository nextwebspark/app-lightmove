/// <reference types="vitest/config" />
import { writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { buildManifest } from "./manifest.config";
import { PLACEHOLDER_WORKSPACE_ORIGIN, resolveWorkspaceOrigin } from "./src/buildTargets";

/**
 * The popup and the service worker.
 *
 * The page reader is built separately (`vite.page-reader.config.ts`) for one reason: it is injected by
 * `chrome.scripting` as a classic script, not a module, so it has to be bundled as an IIFE. Everything
 * here is ES — the popup is a page and the service worker declares `type: module`.
 */
export default defineConfig(({ mode }) => {
  // Loaded with an empty prefix so LM_WORKSPACE_ORIGIN is visible without a VITE_ prefix — it is a
  // build parameter, not something the bundle reads for itself.
  const workspaceOrigin = resolveWorkspaceOrigin(mode, loadEnv(mode, process.cwd(), ""));

  if (workspaceOrigin === PLACEHOLDER_WORKSPACE_ORIGIN) {
    // Warned, not thrown: CI builds this to typecheck and bundle, and failing there would say nothing
    // useful. What must not happen is somebody shipping it, so the warning names the fix.
    console.warn(
      "\n\x1b[33m  ⚠  No LM_WORKSPACE_ORIGIN — built against the placeholder %s.\x1b[0m\n" +
        "\x1b[33m     This build cannot talk to any real workspace. Do not publish it.\x1b[0m\n" +
        "\x1b[33m     Set LM_WORKSPACE_ORIGIN to your domain, or to the Cloud Run URL the deploy prints.\x1b[0m\n",
      PLACEHOLDER_WORKSPACE_ORIGIN,
    );
  }

  return {
  plugins: [
    react(),
    tailwindcss(),
    {
      name: "lightmove-emit-manifest",
      // `writeBundle`, not `generateBundle`: the manifest is not part of the bundle graph and has no
      // business being hashed, watched, or transformed alongside the code.
      writeBundle() {
        // Resolved against this file, not the process's cwd. `build.outDir` below is relative to
        // Vite's root, so a `vite build --config apps/extension/vite.config.ts` from the repo root
        // would have written the manifest to /dist and the bundle somewhere else — agreeing today only
        // because every documented invocation happens to run from apps/extension.
        writeFileSync(resolve(import.meta.dirname, "dist/manifest.json"),
          JSON.stringify(buildManifest(workspaceOrigin), null, 2));
      },
    },
  ],

  // The same origin the manifest above names. Substituted as a literal so the two cannot drift.
  define: { __WORKSPACE_ORIGIN__: JSON.stringify(workspaceOrigin) },

  // Icons are copied verbatim from public/ into dist/.
  publicDir: "public",

  build: {
    outDir: "dist",
    // Never emptied by this pass, and that is load-bearing rather than cautious. The page reader is a
    // second build writing into the same dist/, and in watch mode Vite re-empties on every rebuild —
    // so editing a popup file would delete the bundle the manifest injects, and page reading would
    // stay broken until something happened to touch the reader. `npm run clean` empties dist once.
    emptyOutDir: false,
    minify: true,
    // "hidden" emits the maps without the sourceMappingURL comment: packageRelease.mjs strips every
    // map from the uploaded archive, so plain `true` would leave devtools 404ing on the published build.
    sourcemap: "hidden",
    rollupOptions: {
      input: {
        popup: resolve(import.meta.dirname, "popup.html"),
        background: resolve(import.meta.dirname, "src/background/serviceWorker.ts"),
      },
      output: {
        // Fixed names, because manifest.config.ts names these files. A hash here would break the
        // manifest on every build.
        entryFileNames: "[name].js",
        chunkFileNames: "chunks/[name]-[hash].js",
        assetFileNames: "assets/[name]-[hash][extname]",
      },
    },
  },

  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
  },
  };
});
