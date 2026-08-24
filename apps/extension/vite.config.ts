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
        writeFileSync(resolve("dist/manifest.json"),
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
    emptyOutDir: true,
    minify: true,
    // Minified but never obfuscated, and shipped with maps. A popup is opened and thrown away dozens
    // of times a day, so parse time is felt; the maps are what keep the bundle reviewable, by Chrome's
    // reviewers and ours, without paying 700 kB of unminified React for it.
    sourcemap: true,
    rollupOptions: {
      input: {
        popup: resolve("popup.html"),
        background: resolve("src/background/serviceWorker.ts"),
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
    globals: true,
  },
  };
});
