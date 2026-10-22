import { defineConfig } from "vitest/config";

/** The tests that need a real Chromium (`CHROME_PATH`); `npm test` runs the rest without one. */
export default defineConfig({
  test: {
    include: ["src/**/*.test.browser.ts"],
    fileParallelism: false,
  },
});
