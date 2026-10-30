import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ICONS } from "../../../components/layout/Icon";
import { NoteKindMark } from "./NoteKindMark";

describe("NoteKindMark", () => {
  it("draws a call with a handset, an email with an envelope and a meeting with a camera, whichever case it is sent in", () => {
    const pathOf = (kind: string) =>
      render(<NoteKindMark kind={kind} size={14} />).container.querySelector("path")?.getAttribute("d");
    expect(pathOf("CALL")).toBe(ICONS.phoneCall);
    expect(pathOf("email")).toBe(ICONS.mail);
    expect(pathOf("MEETING")).toBe(ICONS.video);
    expect(pathOf("unheard-of")).toBe(ICONS.fileText);
  });

  it("draws LinkedIn as the network's own mark, never a stroke glyph", () => {
    const { container } = render(<NoteKindMark kind="LINKEDIN" size={14} />);
    expect(container.querySelector("svg")).toBeNull();
    expect([...container.querySelectorAll("img")].map((img) => img.getAttribute("src"))).toEqual([
      "/brand/linkedin-in-bug-black.png",
      "/brand/linkedin-in-bug-white.png",
    ]);
  });
});
