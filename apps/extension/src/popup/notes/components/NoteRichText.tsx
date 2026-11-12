// Ported from apps/web/src/features/notes/components/NoteRichText.tsx; keep the two in step.
import type { JSONContent } from "@tiptap/core";
import { renderToReactElement } from "@tiptap/static-renderer";
import { useMemo } from "react";
import { cn } from "../../lib/cn";
import { MentionChip } from "./NoteBody";
import { isSafeHref, mentionOf, noteExtensions, parseNoteMarkdown } from "../lib/noteMarkdown";
import { NOTE_PROSE } from "../lib/noteProse";

const EXTENSIONS = noteExtensions();

/**
 * A formatted note, drawn as React from the same schema the editor writes — never as HTML — so whatever a body
 * holds becomes these elements or nothing. A link leaves only for http, https or mailto, and in a new tab.
 */
export default function NoteRichText({ body, className }: { body: string; className?: string }) {
  const rendered = useMemo(() => renderNote(parseNoteMarkdown(body)), [body]);
  return <div className={cn(NOTE_PROSE, className)}>{rendered}</div>;
}

function renderNote(content: JSONContent) {
  return renderToReactElement({
    content,
    extensions: EXTENSIONS,
    options: {
      nodeMapping: {
        mention: ({ node }) => <MentionChip mention={mentionOf(node.attrs.id, node.attrs.label)} />,
        taskItem: ({ node, children }) => (
          <li data-type="taskItem" data-checked={node.attrs.checked ? "true" : "false"}>
            <label>
              <input
                type="checkbox"
                checked={Boolean(node.attrs.checked)}
                readOnly
                disabled
                aria-label={node.attrs.checked ? "Done" : "Not done"}
                className="accent-u-accent"
              />
            </label>
            <div>{children}</div>
          </li>
        ),
      },
      markMapping: {
        link: ({ mark, children }) => {
          const href = typeof mark.attrs.href === "string" ? mark.attrs.href : null;
          return isSafeHref(href) ? (
            <a href={href ?? undefined} target="_blank" rel="noopener noreferrer">
              {children}
            </a>
          ) : (
            <>{children}</>
          );
        },
      },
    },
  });
}
