import { describe, expect, it } from "vitest";
import onFile from "../../../../api/src/main/resources/screen-text/catalog.json?raw";
import { NEVER_ON_SCREEN } from "../neverOnScreen";
import { catalogFileOf } from "./catalogFile";
import { SCREEN_TEXT_CATALOG } from "./index";

const CATALOG_FILE = "../api/src/main/resources/screen-text/catalog.json";
const updating = Boolean(import.meta.env.UPDATE_SCREEN_TEXT_CATALOG);

async function rewrite(content: string): Promise<void> {
  // Loaded only here, as styleRatchet.test.ts does: the app's tsconfig carries no Node types.
  const fs = (await import(/* @vite-ignore */ `node:${"fs"}`)) as {
    writeFileSync: (path: string, data: string) => void;
  };
  fs.writeFileSync(CATALOG_FILE, content);
}

/**
 * The server's copy of the catalogue is this suite's output, the way `docs/public-api/openapi.json` is
 * `PublicApiContractTest`'s. `UPDATE_SCREEN_TEXT_CATALOG=1 npx vitest run src/copy` rewrites it.
 */
describe("screen text catalogue", () => {
  it("is the file the server reads", async () => {
    const expected = catalogFileOf(SCREEN_TEXT_CATALOG);
    if (updating) {
      await rewrite(expected);
      return;
    }
    expect(onFile, "rerun with UPDATE_SCREEN_TEXT_CATALOG=1 to rewrite it").toBe(expected);
  });

  it("gives every screen group a distinct id", () => {
    const ids = SCREEN_TEXT_CATALOG.map((screen) => screen.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it("says where every sentence shows", () => {
    const unexplained = SCREEN_TEXT_CATALOG.flatMap((screen) =>
      Object.entries(screen.entries)
        .filter(([, entry]) => entry.where.trim() === "" || entry.text.trim() === "")
        .map(([local]) => `${screen.id}.${local}`),
    );
    expect(unexplained).toEqual([]);
  });

  it("never ships a default that names a vendor, the deployment or triage", () => {
    const offenders = SCREEN_TEXT_CATALOG.flatMap((screen) =>
      Object.entries(screen.entries)
        .filter(([, entry]) => NEVER_ON_SCREEN.test(entry.text))
        .map(([local, entry]) => `${screen.id}.${local}: ${entry.text}`),
    );
    expect(offenders).toEqual([]);
  });
});
