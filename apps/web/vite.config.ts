/// <reference types="vitest/config" />
import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

/**
 * The id the pinned manifest key produces when the extension is loaded **unpacked**. Right for
 * development and wrong for anything published — the Chrome Web Store assigns its own id — so a real
 * build passes EXTENSION_ID. It must stay in step with the API's `lightmove.web.cors-allowed-origins`.
 */
const DEVELOPMENT_EXTENSION_ID = "kllpamcdcnecpdblgdkehgbhdjdlbofh";

export default defineConfig(({ mode }) => {
  // Loaded with an empty prefix, as apps/extension does, so APP_VERSION and EXTENSION_ID are visible
  // without a VITE_ prefix — they are build parameters, not something the bundle reads for itself.
  // Only the two keys named below are ever inlined; an empty prefix returns the whole environment.
  const env = loadEnv(mode, process.cwd(), "");

  return {
    plugins: [react(), tailwindcss()],

    define: {
      // Stamped by the Dockerfile's build arg, which carries the release tag. Absent in every local
      // build, and `dev` is the honest answer there rather than a version nobody cut.
      __APP_VERSION__: JSON.stringify(env.APP_VERSION || "dev"),
      __EXTENSION_ID__: JSON.stringify(env.EXTENSION_ID || DEVELOPMENT_EXTENSION_ID),
    },

    server: {
      port: 5173,
      /**
       * The API is proxied under the SPA's own origin.
       *
       * Not a convenience — it is what makes the auth model work in development. The refresh token lives
       * in a SameSite cookie scoped to /api/v1/auth. A page served from localhost:5173 calling an API on
       * localhost:8080 is cross-site as far as the browser is concerned, and it would simply decline to
       * attach that cookie. Proxying makes dev behave like production, where the SPA and the API sit
       * behind a single hostname.
       */
      proxy: {
        "/api": { target: "http://localhost:8080", changeOrigin: true },
        // Spring owns these two paths for the Google redirect flow.
        "/oauth2": { target: "http://localhost:8080", changeOrigin: true },
        "/login/oauth2": { target: "http://localhost:8080", changeOrigin: true },
      },
    },

    test: {
      environment: "jsdom",
      globals: true,
      setupFiles: ["./src/test/setup.ts"],
    },
  };
});
