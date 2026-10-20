import type { AnyExtension, JSONContent } from "@tiptap/core";
import type { MentionOptions } from "@tiptap/extension-mention";
import { TaskItem, TaskList } from "@tiptap/extension-list";
import Mention from "@tiptap/extension-mention";
import Paragraph from "@tiptap/extension-paragraph";
import { MarkdownManager } from "@tiptap/markdown";
import StarterKit from "@tiptap/starter-kit";
import { Marked } from "marked";
import { mentionToken, parseNoteBody, type Mention as NoteMention } from "../../tasks/lib/mentions";
import type { NoteFormat } from "./noteFormat";

/**
 * A note's formatting, one schema for the editor, the renderer and the stored Markdown, so what is typed is what is
 * shown. Stored as CommonMark + GFM with `++underline++`; a mention keeps the server's own token
 * (`@[Name](user:<id>)`, `@[team](team)`), never Tiptap's shortcode, because the server reads mentions off it.
 */

export type { NoteFormat } from "./noteFormat";

const MENTION_TOKEN = /^@\[([^\]\n]{1,120})\]\((?:user:([A-Za-z0-9-]+)|(team))\)/;

export const TEAM_MENTION_ID = "team";

/** Only these leave a note as a link; anything else (javascript:, data:) is text. */
export function isSafeHref(href: string | null | undefined): boolean {
  if (!href) return false;
  try {
    const url = new URL(href, "https://invalid.example");
    return url.origin !== "https://invalid.example" && ["http:", "https:", "mailto:"].includes(url.protocol);
  } catch {
    return false;
  }
}

export const NoteMentionNode = Mention.extend({
  markdownTokenizer: {
    name: "mention",
    level: "inline",
    start: (src: string) => src.indexOf("@["),
    tokenize(src: string) {
      const match = MENTION_TOKEN.exec(src);
      if (!match) return undefined;
      return {
        type: "mention",
        raw: match[0],
        label: match[1].trim(),
        mentionId: match[3] ? TEAM_MENTION_ID : match[2],
      };
    },
  },
  parseMarkdown(token, helpers) {
    return helpers.createNode("mention", { id: token.mentionId, label: token.label });
  },
  renderMarkdown(node) {
    return mentionToken(mentionOf(node.attrs?.id, node.attrs?.label));
  },
});

/**
 * Text that would read as a block once stored — "1. ", "- ", "# ", "> ", "---", a setext "=", a table's "|" — is
 * escaped where the paragraph is written, since Tiptap escapes inline markers only and a plain note full of them would
 * otherwise come back as lists and headings. An indent of four spaces or a tab would read as a code block, which the
 * schema has no node for, and the line would be dropped, so its first character goes as an entity.
 */
const NoteParagraph = Paragraph.extend({
  renderMarkdown(node, helpers, context) {
    const written = this.parent?.(node, helpers, context) ?? "";
    return written
      .split("\n")
      .map((line) =>
        line
          .replace(/^(\s{0,3})(\d{1,9})([.)])(?=\s|$)/, "$1$2\\$3")
          .replace(/^(\s{0,3})([-+*>]|#{1,6}|=+)(?=\s|$|-|=)/, "$1\\$2")
          .replace(/^(?= {4}| {0,3}\t)[ \t]/, (indent) => (indent === "\t" ? "&#9;" : "&#32;")),
      )
      .join("\n");
  },
});

/**
 * A literal "++" would read as underline and a "|" as a table, which the schema has no node for (the whole note would
 * be dropped); nothing else Tiptap leaves unescaped is a note's markup.
 */
function escapePlusRuns(manager: MarkdownManager): MarkdownManager {
  const internals = manager as unknown as { escapeMarkdownSyntax: (text: string) => string };
  const escapeInline = internals.escapeMarkdownSyntax.bind(manager);
  internals.escapeMarkdownSyntax = (text) =>
    escapeInline(text)
      .replace(/\+{2,}/g, (run) => "\\+".repeat(run.length))
      .replace(/\|/g, "\\|");
  return manager;
}

export function mentionOf(id: string | null | undefined, label: string | null | undefined): NoteMention {
  return id === TEAM_MENTION_ID || !id
    ? { kind: "team" }
    : { kind: "user", userId: id, name: label ?? "" };
}

/** The schema every note surface shares; the editor alone hands the mention its picker. */
export function noteExtensions(mention?: Partial<MentionOptions>): AnyExtension[] {
  return [
    StarterKit.configure({
      paragraph: false,
      code: false,
      codeBlock: false,
      horizontalRule: false,
      heading: { levels: [2, 3] },
      link: {
        openOnClick: false,
        autolink: true,
        defaultProtocol: "https",
        protocols: ["mailto"],
        isAllowedUri: (url) => isSafeHref(url),
      },
    }),
    NoteParagraph,
    TaskList,
    TaskItem.configure({ nested: true }),
    mention ? NoteMentionNode.configure(mention) : NoteMentionNode,
  ];
}

let manager: MarkdownManager | null = null;

/** A manager over its own Marked instance: the shared global one would carry every other manager's tokenizers. */
function markdownManager(): MarkdownManager {
  manager ??= escapePlusRuns(new MarkdownManager({ marked: new Marked() as never, extensions: noteExtensions() }));
  return manager;
}

export function parseNoteMarkdown(body: string): JSONContent {
  return markdownManager().parse(body);
}

/** The document as stored. Not trimmed: a space typed after a mention is the next word's, until the note is saved. */
export function serializeNoteDoc(doc: JSONContent): string {
  return markdownManager().serialize(doc).replace(/\n+$/, "");
}

/**
 * A plain note as a document: each line a paragraph, its mention tokens as mention nodes, nothing read as markup —
 * so a pasted "*" or "1." in an old note stays what it was when the note is next edited.
 */
export function plainNoteDoc(body: string): JSONContent {
  const paragraphs = body.split(/\r?\n/).map((line): JSONContent => {
    const content: JSONContent[] = parseNoteBody(line).flatMap((segment): JSONContent[] => {
      if (segment.kind === "text") return segment.text ? [{ type: "text", text: segment.text }] : [];
      const mention = segment.mention;
      return [
        {
          type: "mention",
          attrs: mention.kind === "team" ? { id: TEAM_MENTION_ID, label: "team" } : { id: mention.userId, label: mention.name },
        },
      ];
    });
    return content.length > 0 ? { type: "paragraph", content } : { type: "paragraph" };
  });
  return { type: "doc", content: paragraphs };
}

export function noteDocOf(body: string, format: NoteFormat): JSONContent {
  return format === "markdown" ? parseNoteMarkdown(body) : plainNoteDoc(body);
}
