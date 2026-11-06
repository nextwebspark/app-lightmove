import { describe, expect, it } from "vitest";
import { workspaceOrigin } from "../../workspaceOrigin";
import { avatarSrc } from "./avatarSrc";

describe("avatarSrc", () => {
  it("puts a stored photo's relative link on the workspace origin", () => {
    expect(avatarSrc("/api/v1/avatars/abc")).toBe(`${workspaceOrigin}/api/v1/avatars/abc`);
  });

  it("leaves absolute links, data URLs and protocol-relative links alone", () => {
    expect(avatarSrc("https://media.example/alok.jpg")).toBe("https://media.example/alok.jpg");
    expect(avatarSrc("data:image/jpeg;base64,AAAA")).toBe("data:image/jpeg;base64,AAAA");
    expect(avatarSrc("//evil.example/x.jpg")).toBe("//evil.example/x.jpg");
  });

  it("has nothing to draw without a link", () => {
    expect(avatarSrc(null)).toBeNull();
    expect(avatarSrc(undefined)).toBeNull();
    expect(avatarSrc("")).toBeNull();
  });
});
