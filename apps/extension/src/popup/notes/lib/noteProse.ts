// Ported from apps/web/src/features/notes/lib/noteProse.ts; keep the two in step.
/** How a formatted note reads, shared by the editor and the card so a note looks the same typed and saved; the
 * checklist and the placeholder are `.note-prose` in global.css. */
export const NOTE_PROSE = [
  "note-prose text-body text-u-text2 break-words",
  "[&_p]:my-0 [&_p+p]:mt-1.5",
  "[&_h2]:mt-2 [&_h2]:mb-1 [&_h2]:text-lead [&_h2]:font-semibold [&_h2]:text-u-text",
  "[&_h3]:mt-2 [&_h3]:mb-1 [&_h3]:text-body [&_h3]:font-semibold [&_h3]:text-u-text",
  "[&_ul]:my-1 [&_ul]:list-disc [&_ul]:ps-5 [&_ol]:my-1 [&_ol]:list-decimal [&_ol]:ps-5",
  "[&_blockquote]:my-1.5 [&_blockquote]:border-s-2 [&_blockquote]:border-u-border [&_blockquote]:ps-3 [&_blockquote]:text-u-text3",
  "[&_a]:text-u-accent [&_a]:underline [&_a]:underline-offset-2",
  "[&_strong]:font-semibold [&_strong]:text-u-text",
].join(" ");
