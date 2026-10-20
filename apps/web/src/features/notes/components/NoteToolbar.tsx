import type { Editor } from "@tiptap/core";
import { useEditorState } from "@tiptap/react";
import { Icon } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";

/** Lucide's strokes, drawn by the app's own Icon. */
const GLYPHS = {
  bold: "M6 4h8a4 4 0 0 1 0 8H6zM6 12h9a4 4 0 0 1 0 8H6z",
  italic: "M19 4h-9M14 20H5M14.7 4.5 9.3 19.5",
  underline: "M6 4v6a6 6 0 0 0 12 0V4M4 20h16",
  strike: "M16 4H9a3 3 0 0 0-2.83 4M14 12a4 4 0 0 1 0 8H6M4 12h16",
  bulletList: "M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01",
  orderedList: "M10 6h11M10 12h11M10 18h11M4 6h1v4M4 10h2M6 18H4c0-1 2-2 2-3s-1-1.5-2-1",
  taskList: "m3 17 2 2 4-4M3 7l2 2 4-4M13 6h8M13 12h8M13 18h8",
  quote: "M3 21c3 0 7-1 7-8V5c0-1.25-.756-2.017-2-2H4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2 1 0 1 0 1 1v1c0 1-1 2-2 2s-1 .008-1 1.031V20c0 1 0 1 1 1zM15 21c3 0 7-1 7-8V5c0-1.25-.757-2.017-2-2h-4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2h.75c0 2.25.25 4-2.75 4v3c0 1 0 1 1 1z",
  link: "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71",
  expand: "M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7",
} as const;

/**
 * The note editor's formatting row: marks, two heading sizes, three kinds of list, a quote, a link, a mention and
 * Expand. Each button says whether it is on, and keeps the selection it acts on.
 */
export function NoteToolbar({
  editor,
  mentionsOffered,
  pickerOpen,
  onMention,
  onLink,
  expanded,
  onExpand,
}: {
  editor: Editor | null;
  mentionsOffered: boolean;
  pickerOpen: boolean;
  onMention: () => void;
  onLink: () => void;
  expanded: boolean;
  onExpand: () => void;
}) {
  const active = useEditorState({
    editor,
    selector: ({ editor: current }) => ({
      bold: current?.isActive("bold") ?? false,
      italic: current?.isActive("italic") ?? false,
      underline: current?.isActive("underline") ?? false,
      strike: current?.isActive("strike") ?? false,
      heading: current?.isActive("heading", { level: 2 }) ?? false,
      subheading: current?.isActive("heading", { level: 3 }) ?? false,
      bulletList: current?.isActive("bulletList") ?? false,
      orderedList: current?.isActive("orderedList") ?? false,
      taskList: current?.isActive("taskList") ?? false,
      quote: current?.isActive("blockquote") ?? false,
      link: current?.isActive("link") ?? false,
    }),
  });
  const run = (action: (chain: ReturnType<Editor["chain"]>) => ReturnType<Editor["chain"]>) => () => {
    if (editor) action(editor.chain().focus()).run();
  };

  return (
    <div role="toolbar" aria-label="Formatting" className="flex flex-wrap items-center gap-0.5">
      <ToolButton label="Bold" shortcut="⌘B" pressed={active?.bold} glyph={GLYPHS.bold} onClick={run((c) => c.toggleBold())} />
      <ToolButton label="Italic" shortcut="⌘I" pressed={active?.italic} glyph={GLYPHS.italic} onClick={run((c) => c.toggleItalic())} />
      <ToolButton label="Underline" shortcut="⌘U" pressed={active?.underline} glyph={GLYPHS.underline} onClick={run((c) => c.toggleUnderline())} />
      <ToolButton label="Strikethrough" pressed={active?.strike} glyph={GLYPHS.strike} onClick={run((c) => c.toggleStrike())} />
      <Divider />
      <ToolButton label="Heading" pressed={active?.heading} text="H2" onClick={run((c) => c.toggleHeading({ level: 2 }))} />
      <ToolButton label="Subheading" pressed={active?.subheading} text="H3" onClick={run((c) => c.toggleHeading({ level: 3 }))} />
      <Divider />
      <ToolButton label="Bulleted list" pressed={active?.bulletList} glyph={GLYPHS.bulletList} onClick={run((c) => c.toggleBulletList())} />
      <ToolButton label="Numbered list" pressed={active?.orderedList} glyph={GLYPHS.orderedList} onClick={run((c) => c.toggleOrderedList())} />
      <ToolButton label="Checklist" pressed={active?.taskList} glyph={GLYPHS.taskList} onClick={run((c) => c.toggleTaskList())} />
      <ToolButton label="Quote" pressed={active?.quote} glyph={GLYPHS.quote} onClick={run((c) => c.toggleBlockquote())} />
      <ToolButton label="Link" shortcut="⌘K" pressed={active?.link} glyph={GLYPHS.link} onClick={onLink} />
      {mentionsOffered && (
        <ToolButton label="Mention a teammate" shortcut="@" pressed={pickerOpen} text="@" onClick={onMention} />
      )}
      {!expanded && (
        <>
          <span className="ms-auto" />
          <ToolButton label="Expand" glyph={GLYPHS.expand} onClick={onExpand} />
        </>
      )}
    </div>
  );
}

function ToolButton({
  label,
  shortcut,
  pressed,
  glyph,
  text,
  onClick,
}: {
  label: string;
  shortcut?: string;
  pressed?: boolean;
  glyph?: string;
  text?: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      aria-pressed={pressed === undefined ? undefined : pressed}
      title={shortcut ? `${label} (${shortcut})` : label}
      onMouseDown={(event) => event.preventDefault()}
      onClick={onClick}
      className={cn(
        "grid h-7 min-w-7 place-items-center rounded-u-control px-1 text-note font-semibold text-u-text3 hover:bg-u-raised hover:text-u-text",
        pressed && "bg-u-accent-tint text-u-accent hover:bg-u-accent-tint hover:text-u-accent",
      )}
    >
      {glyph ? <Icon d={glyph} size={15} /> : text}
    </button>
  );
}

function Divider() {
  return <span aria-hidden className="mx-1 h-4 w-px bg-u-border" />;
}
