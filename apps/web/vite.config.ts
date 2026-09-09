/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

/**
 * The id the pinned manifest key produces when the extension is loaded **unpacked**. Right for
 * development and wrong for anything published — the Chrome Web Store assigns its own id — so a real
 * build passes EXTENSION_ID. It must stay in step with the API's `lightmove.web.cors-allowed-origins`.
 */
const DEVELOPMENT_EXTENSION_ID = "kllpamcdcnecpdblgdkehgbhdjdlbofh";

export default defineConfig({
  plugins: [react(), tailwindcss()],

  // Build parameters, not settings the bundle reads for itself, so they are frozen in here rather than
  // exposed as VITE_-prefixed env. Both arrive as real environment variables from the Dockerfile's
  // build args, and are read straight off process.env — no .env file, and no dependence on the
  // directory the command was launched from.
  //
  // This is the one place the version is decided, and it decides it for both halves of the image: the
  // same APP_VERSION reaches Spring as an env var, so the SPA and the API cannot drift apart.
  define: {
    __APP_VERSION__: JSON.stringify(process.env.APP_VERSION || "dev"),
    __EXTENSION_ID__: JSON.stringify(process.env.EXTENSION_ID || DEVELOPMENT_EXTENSION_ID),
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
});
