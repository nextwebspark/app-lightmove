import { describe, expect, it } from "vitest";
import {
  drawerSectionOf,
  drawerTabOf,
  POSITION_CLIENT_TABS,
  POSITION_STAFF_TABS,
  rememberDrawerTab,
  rememberedDrawerTab,
  WORKSPACE_TABS,
} from "./drawerTabs";

describe("drawerTabOf", () => {
  it("keeps a tab the drawer offers", () => {
    expect(drawerTabOf("positions", WORKSPACE_TABS)).toBe("positions");
    expect(drawerTabOf("outreach", POSITION_STAFF_TABS)).toBe("outreach");
  });

  it("sends the retired tab names to where their content now lives", () => {
    expect(drawerTabOf("records", WORKSPACE_TABS)).toBe("notes");
    expect(drawerTabOf("timeline", WORKSPACE_TABS)).toBe("activity");
    expect(drawerTabOf("career", WORKSPACE_TABS)).toBe("profile");
    expect(drawerTabOf("background", POSITION_STAFF_TABS)).toBe("profile");
    expect(drawerTabOf("compensation", POSITION_STAFF_TABS)).toBe("profile");
    expect(drawerTabOf("documents", WORKSPACE_TABS)).toBe("profile");
    expect(drawerTabOf("contact", POSITION_STAFF_TABS)).toBe("outreach");
  });

  it("opens on the first tab for anything else, or a tab this seat is not offered", () => {
    expect(drawerTabOf(null, WORKSPACE_TABS)).toBe("profile");
    expect(drawerTabOf("nonsense", WORKSPACE_TABS)).toBe("profile");
    expect(drawerTabOf("notes", POSITION_CLIENT_TABS)).toBe("profile");
    expect(drawerTabOf("contact", WORKSPACE_TABS)).toBe("profile");
    expect(drawerTabOf("assessment", WORKSPACE_TABS)).toBe("profile");
  });
});

describe("drawerSectionOf", () => {
  it("keeps the parts a link may name", () => {
    expect(drawerSectionOf("contact")).toBe("contact");
    expect(drawerSectionOf("meetings")).toBe("meetings");
    expect(drawerSectionOf("nonsense")).toBeNull();
  });
});

describe("the remembered tab", () => {
  it("reopens a drawer kind on the tab last chosen, within what this drawer offers", () => {
    sessionStorage.clear();
    expect(rememberedDrawerTab("position", POSITION_STAFF_TABS)).toBe("profile");

    rememberDrawerTab("position", "outreach");

    expect(rememberedDrawerTab("position", POSITION_STAFF_TABS)).toBe("outreach");
    expect(rememberedDrawerTab("position", POSITION_CLIENT_TABS)).toBe("profile");
    expect(rememberedDrawerTab("workspace", WORKSPACE_TABS)).toBe("profile");
  });

  it("never remembers the copilot, which lasts one call", () => {
    sessionStorage.clear();
    rememberDrawerTab("position", "notes");
    rememberDrawerTab("position", "copilot");

    expect(rememberedDrawerTab("position", POSITION_STAFF_TABS)).toBe("notes");
  });
});
