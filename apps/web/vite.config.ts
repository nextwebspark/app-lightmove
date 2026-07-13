/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],

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
