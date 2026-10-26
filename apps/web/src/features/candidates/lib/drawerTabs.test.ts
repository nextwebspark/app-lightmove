import { describe, expect, it } from "vitest";
import {
  drawerTabOf,
  POSITION_CLIENT_TABS,
  POSITION_STAFF_TABS,
  rememberDrawerTab,
  rememberedDrawerTab,
  WORKSPACE_TABS,
} from "./drawerTabs";

describe("drawerTabOf", () => {
  it("keeps a tab the drawer offers", () => {
    expect(drawerTabOf("documents", WORKSPACE_TABS)).toBe("documents");
  });

  it("sends the retired tab names to where their content now lives", () => {
    expect(drawerTabOf("records", WORKSPACE_TABS)).toBe("notes");
    expect(drawerTabOf("timeline", WORKSPACE_TABS)).toBe("activity");
    expect(drawerTabOf("profile", POSITION_STAFF_TABS)).toBe("career");
  });

  it("opens on the first tab for anything else, or a tab this seat is not offered", () => {
    expect(drawerTabOf(null, WORKSPACE_TABS)).toBe("career");
    expect(drawerTabOf("nonsense", WORKSPACE_TABS)).toBe("career");
    expect(drawerTabOf("notes", POSITION_CLIENT_TABS)).toBe("career");
  });
});

describe("the remembered tab", () => {
  it("reopens a drawer kind on the tab last chosen, within what this drawer offers", () => {
    sessionStorage.clear();
    expect(rememberedDrawerTab("position", POSITION_STAFF_TABS)).toBe("career");

    rememberDrawerTab("position", "documents");

    expect(rememberedDrawerTab("position", POSITION_STAFF_TABS)).toBe("documents");
    expect(rememberedDrawerTab("position", POSITION_CLIENT_TABS)).toBe("career");
    expect(rememberedDrawerTab("workspace", WORKSPACE_TABS)).toBe("career");
  });

  it("never remembers the copilot, which lasts one call", () => {
    sessionStorage.clear();
    rememberDrawerTab("position", "notes");
    rememberDrawerTab("position", "copilot");

    expect(rememberedDrawerTab("position", POSITION_STAFF_TABS)).toBe("notes");
  });
});
