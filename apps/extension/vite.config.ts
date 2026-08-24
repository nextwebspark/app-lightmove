/// <reference types="vitest/config" />
import { writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { buildManifest } from "./manifest.config";

/**
 * The popup and the service worker.
 *
 * The pairing content script is built separately (`vite.content.config.ts`) and for one reason: a
 * Manifest V3 content script is a classic script, not a module, so it has to be bundled as an IIFE.
 * Everything here is ES — the popup is a page and the service worker declares `type: module`.
 */
export default defineConfig(({ mode }) => ({
  plugins: [
    react(),
    tailwindcss(),
    {
      name: "lightmove-emit-manifest",
      // `writeBundle`, not `generateBundle`: the manifest is not part of the bundle graph and has no
      // business being hashed, watched, or transformed alongside the code.
      writeBundle() {
        const manifest = buildManifest(mode === "production");
        writeFileSync(resolve("dist/manifest.json"), JSON.stringify(manifest, null, 2));
      },
    },
  ],

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
}));
