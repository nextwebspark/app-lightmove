import { Extension, type Editor } from "@tiptap/core";
import type { MentionNodeAttrs } from "@tiptap/extension-mention";
import { Placeholder } from "@tiptap/extensions";
import { Selection } from "@tiptap/pm/state";
import { EditorContent, useEditor, useEditorState } from "@tiptap/react";
import type { SuggestionKeyDownProps, SuggestionProps } from "@tiptap/suggestion";
import { useEffect, useMemo, useRef, useState, type FormEvent, type MouseEvent } from "react";
import { Button, Modal } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { isPressOnEmptySpace } from "../../../lib/focusFieldWithin";
import { MentionChip } from "../../tasks/components/NoteBody";
import { mentionKey, type Mention } from "../../tasks/lib/mentions";
import { isSafeHref, mentionOf, noteDocOf, noteExtensions, serializeNoteDoc, TEAM_MENTION_ID } from "../lib/noteMarkdown";
import { NOTE_PROSE } from "../lib/noteProse";
import type { NoteEditorProps } from "./NoteEditor";
import { MentionPickerList, mentionOptionsFor, type MentionPickerOption } from "./MentionPickerList";
import { NoteToolbar } from "./NoteToolbar";

interface Picker {
  options: MentionPickerOption[];
  active: number;
  pick: (attrs: MentionNodeAttrs) => void;
  /** Where the `@` being typed is on screen, when the editor can say. */
  caret: (() => DOMRect | null) | null;
}

/**
 * A note typed with formatting (bold, lists, a checklist, links, @mentions) and kept as Markdown. The same composer —
 * the editor with its `header` and `footer` — opens in a larger view on Expand, moved rather than copied, so the
 * words, the undo history, the kind, who reads it and the save all go with it. A save that empties it closes the view.
 */
export default function RichNoteEditor({
  value,
  format,
  onChange,
  teammates,
  ariaLabel,
  placeholder,
  onSubmit,
  submitLabel = "Save",
  canSubmit = true,
  expandTitle = "Note",
  variant = "note",
  roomy = false,
  autoFocus = false,
  header,
  footer,
  fillsDialog = false,
  onExpandChange,
}: NoteEditorProps) {
  const [picker, setPicker] = useState<Picker | null>(null);
  const [expanded, setExpanded] = useState(false);
  const [linkOpen, setLinkOpen] = useState(false);
  const pickerRef = useRef<Picker | null>(null);
  const surfaceRef = useRef<HTMLDivElement>(null);
  const teammatesRef = useRef(teammates);
  const onChangeRef = useRef(onChange);
  const onSubmitRef = useRef(onSubmit);
  const canSubmitRef = useRef(canSubmit);
  const lastEmitted = useRef(value);
  useEffect(() => {
    teammatesRef.current = teammates;
    onChangeRef.current = onChange;
    onSubmitRef.current = onSubmit;
    canSubmitRef.current = canSubmit;
  });

  const toggleExpanded = () => {
    setExpanded(!expanded);
    onExpandChange?.(!expanded);
  };

  const showPicker = (next: Picker | null) => {
    pickerRef.current = next;
    setPicker(next);
  };

  const extensions = useMemo(
    () => [
      ...noteExtensions({
        suggestion: {
          char: "@",
          allow: () => teammatesRef.current !== null,
          items: ({ query }) => mentionOptionsFor(teammatesRef.current ?? [], query),
          render: () => ({
            onStart: (props: SuggestionProps<MentionPickerOption, MentionNodeAttrs>) =>
              showPicker({ options: props.items, active: 0, pick: props.command, caret: props.clientRect ?? null }),
            onUpdate: (props: SuggestionProps<MentionPickerOption, MentionNodeAttrs>) =>
              showPicker({ options: props.items, active: 0, pick: props.command, caret: props.clientRect ?? null }),
            onKeyDown: ({ event }: SuggestionKeyDownProps) => handlePickerKey(event),
            onExit: () => showPicker(null),
          }),
        },
      }),
      Placeholder.configure({ placeholder: placeholder ?? "" }),
      Extension.create({
        name: "noteShortcuts",
        addKeyboardShortcuts: () => ({
          "Mod-Enter": () => {
            if (onSubmitRef.current && canSubmitRef.current) {
              // Filling a dialog, the size is the dialog's: it closes on a save, and stays as it was on a refusal.
              if (!fillsDialog) setExpanded(false);
              onSubmitRef.current();
            }
            return true;
          },
          "Mod-k": () => {
            setLinkOpen(true);
            return true;
          },
        }),
      }),
    ],
    // The editor is built once; what changes later is read through the refs above.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  const editor = useEditor({
    extensions,
    content: noteDocOf(value, format),
    autofocus: autoFocus ? "end" : false,
    editorProps: {
      attributes: {
        role: "textbox",
        "aria-multiline": "true",
        "aria-label": ariaLabel,
        class: surfaceClassOf(variant, roomy, expanded, fillsDialog),
      },
    },
    onUpdate: ({ editor: changed }) => {
      const body = serializeNoteDoc(changed.getJSON());
      lastEmitted.current = body;
      onChangeRef.current(body);
    },
  });

  useEffect(() => {
    if (!editor || value === lastEmitted.current) return;
    lastEmitted.current = value;
    editor.commands.setContent(noteDocOf(value, format), { emitUpdate: false });
    if (value === "" && !fillsDialog) setExpanded(false);
  }, [editor, value, format, fillsDialog]);

  useEffect(() => {
    editor?.setOptions({
      editorProps: {
        attributes: {
          role: "textbox",
          "aria-multiline": "true",
          "aria-label": ariaLabel,
          class: surfaceClassOf(variant, roomy, expanded, fillsDialog),
        },
      },
    });
  }, [editor, ariaLabel, variant, roomy, expanded, fillsDialog]);

  const mentioned = useEditorState({
    editor,
    selector: ({ editor: current }) => (current ? mentionsOf(current) : []),
    equalityFn: (a, b) => (a ?? []).map(mentionKey).join() === (b ?? []).map(mentionKey).join(),
  });

  function handlePickerKey(event: KeyboardEvent): boolean {
    const open = pickerRef.current;
    if (!open) return false;
    if (event.key === "Escape") {
      // The picker is the topmost thing Escape may close: the Expand dialog behind it must stay open.
      event.stopPropagation();
      showPicker(null);
      return true;
    }
    if (open.options.length === 0) return false;
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      const step = event.key === "ArrowDown" ? 1 : -1;
      showPicker({ ...open, active: (open.active + step + open.options.length) % open.options.length });
      return true;
    }
    if (event.key === "Enter" || event.key === "Tab") {
      pickMention(open.options[Math.min(open.active, open.options.length - 1)].mention);
      return true;
    }
    return false;
  }

  function pickMention(mention: Mention) {
    pickerRef.current?.pick(
      mention.kind === "team" ? { id: TEAM_MENTION_ID, label: "team" } : { id: mention.userId, label: mention.name },
    );
    showPicker(null);
  }

  const startMention = () => {
    if (!editor) return;
    const before = editor.state.doc.textBetween(Math.max(0, editor.state.selection.from - 1), editor.state.selection.from);
    editor.chain().focus().insertContent(before === "" || /\s/.test(before) ? "@" : " @").run();
  };

  const removeMention = (mention: Mention) => {
    if (!editor) return;
    const key = mentionKey(mention);
    const ranges: Array<{ from: number; to: number }> = [];
    editor.state.doc.descendants((node, pos) => {
      if (node.type.name === "mention" && mentionKey(mentionOf(node.attrs.id, node.attrs.label)) === key) {
        ranges.push({ from: pos, to: pos + node.nodeSize });
      }
    });
    const transaction = editor.state.tr;
    ranges.reverse().forEach((range) => transaction.delete(range.from, range.to));
    editor.view.dispatch(transaction);
  };

  const avatarOf = (mention: Mention) =>
    mention.kind === "user"
      ? (teammates?.find((teammate) => teammate.userId === mention.userId)?.avatarUrl ?? null)
      : null;

  // The box is taller than the words in it: a press on its empty part, its padding or the toolbar's gap landed outside
  // the editor and left the caret nowhere — the composer read as locked. The link row's form keeps its own presses.
  const focusEditorAtEnd = (event: MouseEvent<HTMLDivElement>) => {
    if (!editor || !isPressOnEmptySpace(event, "form")) return;
    event.preventDefault();
    // Focused now rather than through `focus()`, which waits a frame: a key pressed straight after was lost.
    editor.view.dispatch(editor.state.tr.setSelection(Selection.atEnd(editor.state.doc)));
    editor.view.focus();
  };

  // The larger view, and a composer that is a whole dialog, write in a field of their own under a toolbar band.
  const isFramed = expanded || roomy || fillsDialog;
  const surface = (
    <div
      ref={surfaceRef}
      onMouseDown={focusEditorAtEnd}
      className={cn(
        "relative",
        fillsDialog && "flex min-h-0 flex-1 flex-col",
        isFramed &&
          "rounded-lg border border-u-border-strong bg-u-bg transition focus-within:border-u-accent focus-within:ring-2 focus-within:ring-u-accent-ring",
      )}
    >
      <div className={cn("flex-none", isFramed && "rounded-t-lg border-b border-u-border bg-u-surface px-2 py-1.5")}>
        <NoteToolbar
          editor={editor}
          mentionsOffered={teammates !== null}
          pickerOpen={picker !== null}
          onMention={startMention}
          onLink={() => setLinkOpen((open) => !open)}
          expanded={expanded}
          onExpand={toggleExpanded}
        />
        {linkOpen && editor && <LinkRow editor={editor} onClose={() => setLinkOpen(false)} />}
      </div>
      <EditorContent
        editor={editor}
        className={cn(
          isFramed ? "cursor-text px-4 py-3" : "mt-1.5",
          fillsDialog && "min-h-40 flex-1 overflow-y-auto",
        )}
      />
      {teammates !== null && picker && (
        <MentionPickerList
          anchor={() => picker.caret?.() ?? surfaceRef.current?.getBoundingClientRect() ?? null}
          options={picker.options}
          active={picker.active}
          teammates={teammates}
          onActive={(index) => showPicker({ ...picker, active: index })}
          onPick={pickMention}
        />
      )}
      {mentioned && mentioned.length > 0 && (
        <div
          className={cn(
            "mt-2 flex flex-none flex-wrap items-center gap-1.5 border-t border-u-border pt-2",
            isFramed && "mx-4 mb-3",
          )}
        >
          <span className="text-meta text-u-text3">Notifies</span>
          {mentioned.map((mention) => (
            <MentionChip
              key={mentionKey(mention)}
              mention={mention}
              avatarUrl={avatarOf(mention)}
              onRemove={() => removeMention(mention)}
            />
          ))}
        </div>
      )}
    </div>
  );

  const composer = (
    <>
      {header}
      <div className={cn(header !== undefined && "mt-2")}>{surface}</div>
    </>
  );

  // Filling a dialog, `NoteEditor` lays out what sits above and below the words; only the words scroll.
  if (fillsDialog) return surface;
  if (!expanded) {
    return (
      <>
        {composer}
        {footer && <div className="mt-2">{footer}</div>}
      </>
    );
  }
  return (
    <>
      <p className="rounded-u-control border border-dashed border-u-border px-3 py-2 text-note text-u-text3">
        Editing in the larger view…
      </p>
      <Modal
        open
        onClose={() => setExpanded(false)}
        title={expandTitle}
        className="md:w-[880px]"
        footer={
          footer ? (
            <div className="w-full">{footer}</div>
          ) : onSubmit ? (
            <Button
              type="button"
              size="md"
              disabled={!canSubmit}
              onClick={() => {
                setExpanded(false);
                onSubmit();
              }}
            >
              {submitLabel}
            </Button>
          ) : undefined
        }
      >
        {composer}
      </Modal>
    </>
  );
}

function surfaceClassOf(
  variant: NoteEditorProps["variant"],
  roomy: boolean,
  expanded: boolean,
  fillsDialog: boolean,
): string {
  if (fillsDialog) return cn(NOTE_PROSE, "min-h-full outline-none");
  return cn(
    NOTE_PROSE,
    "outline-none",
    expanded ? "min-h-[50vh]" : roomy ? "min-h-40" : variant === "note" ? "min-h-14" : "min-h-20",
    // Inline, a long note scrolls inside its editor rather than stretching the drawer; Expand is the room to grow.
    !expanded && "max-h-[45vh] overflow-y-auto",
  );
}

function mentionsOf(editor: Editor): Mention[] {
  const found = new Map<string, Mention>();
  editor.state.doc.descendants((node) => {
    if (node.type.name !== "mention") return;
    const mention = mentionOf(node.attrs.id, node.attrs.label);
    if (!found.has(mentionKey(mention))) found.set(mentionKey(mention), mention);
  });
  return [...found.values()];
}

/** The link under the selection, set or taken off; only http, https and mailto are kept. */
function LinkRow({ editor, onClose }: { editor: Editor; onClose: () => void }) {
  const current = editor.getAttributes("link").href as string | undefined;
  const [href, setHref] = useState(current ?? "");
  const input = useRef<HTMLInputElement>(null);
  useEffect(() => input.current?.focus(), []);
  const withScheme = /^[a-z][a-z0-9+.-]*:/i.test(href.trim()) ? href.trim() : `https://${href.trim()}`;
  const valid = href.trim() !== "" && isSafeHref(withScheme);

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    // React bubbles a submit through the tree and through portals: a form around the editor must not save with it.
    event.stopPropagation();
    if (!valid) return;
    editor.chain().focus().extendMarkRange("link").setLink({ href: withScheme }).run();
    onClose();
  };

  return (
    <form onSubmit={handleSubmit} className="mt-1.5 flex items-center gap-2">
      <input
        ref={input}
        value={href}
        onChange={(event) => setHref(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === "Escape") {
            event.stopPropagation();
            onClose();
            editor.commands.focus();
          }
        }}
        aria-label="Link address"
        placeholder="https://"
        className="min-w-0 flex-1 rounded-u-control border border-u-border bg-u-surface px-2 py-1 text-note text-u-text outline-none focus:border-u-accent"
      />
      <Button type="submit" size="xs" disabled={!valid}>
        Apply
      </Button>
      {current && (
        <Button
          type="button"
          size="xs"
          variant="secondary"
          onClick={() => {
            editor.chain().focus().extendMarkRange("link").unsetLink().run();
            onClose();
          }}
        >
          Remove link
        </Button>
      )}
    </form>
  );
}
