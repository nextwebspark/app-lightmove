import { describe, expect, it } from "vitest";
import { isSafeHref, noteDocOf, parseNoteMarkdown, plainNoteDoc, serializeNoteDoc } from "./noteMarkdown";

const roundTrip = (markdown: string) => serializeNoteDoc(parseNoteMarkdown(markdown));

describe("a note's Markdown", () => {
  it.each([
    "**Bold** and *italic* and ++underlined++ and ~~struck~~",
    "## Call summary\n\nWants Dubai.",
    "### Next\n\n- Send the brief\n- Book a call",
    "1. First\n2. Second",
    "- [ ] Ask for the CV\n- [x] Sent the brief",
    "> Quoted from the client",
    "See [their site](https://example.com) first",
    "Call @[Sara Al-Mansour](user:6f1c2a9e-0000-4000-8000-000000000001) and @[team](team) today",
  ])("reads and writes %j unchanged", (markdown) => {
    expect(roundTrip(markdown)).toBe(markdown);
  });

  it("reads a mention token as a mention node", () => {
    const doc = parseNoteMarkdown("Ask @[Omar Khalil](user:abc-123) first");
    expect(doc.content?.[0].content).toContainEqual(
      expect.objectContaining({ type: "mention", attrs: expect.objectContaining({ id: "abc-123", label: "Omar Khalil" }) }),
    );
  });

  it("keeps a plain note's markup characters as text", () => {
    const body = "*not italic*\n1. not a list\n@[Omar Khalil](user:abc-123) to call\n- dash\n# hash\n> quote\nC++ and ++both++";
    const doc = plainNoteDoc(body);
    expect(doc.content).toHaveLength(7);
    expect(doc.content?.[0].content?.[0]).toEqual({ type: "text", text: "*not italic*" });
    expect(doc.content?.[2].content?.[0]).toMatchObject({ type: "mention", attrs: { id: "abc-123" } });
    const reread = parseNoteMarkdown(serializeNoteDoc(noteDocOf(body, "plain")));
    expect(reread.content?.map((block) => block.type)).toEqual(Array(7).fill("paragraph"));
    expect(reread.content?.[0].content).toEqual([{ type: "text", text: "*not italic*" }]);
    expect(reread.content?.[1].content).toEqual([{ type: "text", text: "1. not a list" }]);
    expect(reread.content?.slice(3).map((block) => block.content)).toEqual([
      [{ type: "text", text: "- dash" }],
      [{ type: "text", text: "# hash" }],
      [{ type: "text", text: "> quote" }],
      [{ type: "text", text: "C++ and ++both++" }],
    ]);
  });

  it("keeps an indented line, a table's pipes and a lone = as the text they were, never dropped or a heading", () => {
    const lines = ["first", "    four spaces", "\tTabbed", "| a | b |", "|---|---|"];
    const reread = parseNoteMarkdown(serializeNoteDoc(plainNoteDoc(lines.join("\n"))));
    expect(reread.content?.map((block) => block.content?.[0])).toEqual(
      lines.map((text) => ({ type: "text", text })),
    );

    const broken = {
      type: "doc",
      content: [
        {
          type: "paragraph",
          content: [
            { type: "text", text: "Hello" },
            { type: "hardBreak" },
            { type: "text", text: "=" },
            { type: "hardBreak" },
            { type: "text", text: "| x | y |" },
            { type: "hardBreak" },
            { type: "text", text: "|---|---|" },
          ],
        },
      ],
    };
    expect(parseNoteMarkdown(serializeNoteDoc(broken))).toEqual(broken);
  });

  it("lets only http, https and mailto through as links", () => {
    expect(isSafeHref("https://example.com")).toBe(true);
    expect(isSafeHref("mailto:a@b.com")).toBe(true);
    expect(isSafeHref("javascript:alert(1)")).toBe(false);
    expect(isSafeHref("data:text/html,x")).toBe(false);
    expect(isSafeHref("/relative")).toBe(false);
  });
});
