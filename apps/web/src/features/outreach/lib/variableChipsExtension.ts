import { Extension, type AnyExtension, type Editor } from "@tiptap/core";
import { Plugin, PluginKey, TextSelection, type EditorState } from "@tiptap/pm/state";
import { Decoration, DecorationSet, type EditorView } from "@tiptap/pm/view";
import { TOKEN_PATTERN } from "./sequenceTokens";
import { CHIP_CLASS, type VariableChip, type VariableChipKind } from "./variableChips";

/** What the chips need from the composer, read at draw time so the editor is built once. */
export interface VariableChipHost {
  /** Null while the executive's values load, or for a name no email fills: the text stays as typed. */
  chipFor: (name: string, fallback: string | null) => VariableChip | null;
  /** `from` and `to` are the variable's words in the document, as they are until the next edit. */
  onChip: (target: { name: string; kind: VariableChipKind; chip: VariableChip; rect: DOMRect; from: number; to: number }) => void;
}

interface ChipRange {
  from: number;
  to: number;
}

interface ChipState {
  decorations: DecorationSet;
  ranges: ChipRange[];
}

const KEY = new PluginKey<ChipState>("variableChips");
const REFRESH = "variableChips.refresh";

/** The chips' extension for one editor, and the way to tell it what to draw. */
export interface VariableChips {
  extension: AnyExtension;
  /** Null while the executive's values load: the words read as typed. Whatever editor is built, now or later, follows. */
  setHost: (host: VariableChipHost | null) => void;
}

/**
 * An email to one person reads each `{{variable}}` as that person's value in a chip, and stays the variable underneath:
 * the words in the document and in the stored Markdown are untouched (a template saved from the email keeps
 * `{{firstName}}`, not a name), and only what is drawn changes. The raw text is hidden and the caret steps over it as one.
 */
export function createVariableChips(): VariableChips {
  const source: { current: VariableChipHost | null } = { current: null };
  const views = new Set<EditorView>();
  const extension = Extension.create({
    name: "variableChips",
    addProseMirrorPlugins: () => [
      new Plugin<ChipState>({
        key: KEY,
        state: {
          init: (_config, state) => chipsOf(state, source),
          apply: (transaction, previous, _old, state) =>
            transaction.docChanged || transaction.getMeta(REFRESH) ? chipsOf(state, source) : previous,
        },
        view: (view) => {
          views.add(view);
          return { destroy: () => views.delete(view) };
        },
        props: {
          decorations: (state) => KEY.getState(state)?.decorations,
          handleKeyDown: (view, event) => stepOverChip(view, event),
        },
      }),
    ],
  });
  return {
    extension,
    setHost: (host) => {
      source.current = host;
      // An editor built before the values arrived, or not yet: drawn again here, or built with them.
      for (const view of views) view.dispatch(view.state.tr.setMeta(REFRESH, true));
    },
  };
}

/** One variable's words, where the chip was, given other words or taken out with "". */
export function replaceRangeInEditor(editor: Editor, from: number, to: number, replacement: string) {
  const transaction = editor.state.tr;
  if (replacement) transaction.insertText(replacement, from, to);
  else transaction.delete(from, to);
  editor.view.dispatch(transaction);
}

/**
 * Every use of one variable in the words replaced by `replacement` ("" takes it out), as one change.
 * `onlyWithoutFallback` leaves the uses a fallback stands in for.
 */
export function replaceVariableInEditor(editor: Editor, name: string, replacement: string, onlyWithoutFallback = false) {
  const edits: Array<{ from: number; to: number }> = [];
  editor.state.doc.descendants((node, position) => {
    if (!node.isText || !node.text) return;
    for (const match of node.text.matchAll(new RegExp(TOKEN_PATTERN.source, "g"))) {
      if (match[1] !== name || (onlyWithoutFallback && typed(match[2]) !== null)) continue;
      const from = position + (match.index ?? 0);
      edits.push({ from, to: from + match[0].length });
    }
  });
  if (edits.length === 0) return;
  const transaction = editor.state.tr;
  for (const edit of edits.reverse()) {
    if (replacement) transaction.insertText(replacement, edit.from, edit.to);
    else transaction.delete(edit.from, edit.to);
  }
  editor.view.dispatch(transaction);
}

function chipsOf(state: EditorState, host: { current: VariableChipHost | null }): ChipState {
  const source = host.current;
  const decorations: Decoration[] = [];
  const ranges: ChipRange[] = [];
  if (source) {
    state.doc.descendants((node, position) => {
      if (!node.isText || !node.text) return;
      for (const match of node.text.matchAll(new RegExp(TOKEN_PATTERN.source, "g"))) {
        const name = match[1];
        const fallback = typed(match[2]);
        const chip = source.chipFor(name, fallback);
        if (!chip) continue;
        const from = position + (match.index ?? 0);
        const to = from + match[0].length;
        ranges.push({ from, to });
        decorations.push(
          Decoration.widget(from, () => chipElement(chip, name, source, from, to), {
            // Drawn after the position at `from`: the caret there is left of the chip, and `to` is right of it.
            side: 1,
            key: `${from}:${to}:${name}:${chip.kind}:${chip.text}:${chip.title}`,
            // A press on a chip opens its popover; it must not also move the caret.
            stopEvent: (event) => event.type === "mousedown" || event.type === "click" || event.type === "keydown",
          }),
          Decoration.inline(from, to, { style: "display:none" }),
          // The caret at `to` would be drawn at the end of the hidden words, where typing and Delete find nothing to act on.
          // An empty element there puts it in the text that follows, or at the end of the line.
          Decoration.widget(to, () => document.createElement("span"), { side: -1, key: `${to}:${name}:end` }),
        );
      }
    });
  }
  return { decorations: DecorationSet.create(state.doc, decorations), ranges };
}

function chipElement(chip: VariableChip, name: string, host: VariableChipHost, from: number, to: number): HTMLElement {
  const element = document.createElement("span");
  element.contentEditable = "false";
  element.className = CHIP_CLASS[chip.kind];
  element.dataset.variable = name;
  element.dataset.chip = chip.kind;
  element.title = chip.title;
  element.setAttribute("role", "button");
  element.setAttribute("aria-label", `${chip.label}: ${chip.kind === "blank" ? "nothing on file" : chip.text}`);
  element.textContent = chip.text;
  // A chip is reached by Tab and pressed with Enter or Space, like any button.
  element.tabIndex = 0;
  element.addEventListener("click", (event) => {
    event.preventDefault();
    host.onChip({ name, kind: chip.kind, chip, rect: element.getBoundingClientRect(), from, to });
  });
  element.addEventListener("keydown", (event) => {
    if (event.key !== "Enter" && event.key !== " ") return;
    event.preventDefault();
    element.click();
  });
  return element;
}

/** The pipe may arrive escaped, and a fallback is read as the server reads it: trimmed, on one line. */
function typed(fallback: string | undefined): string | null {
  const text = fallback?.replace(/\s+/g, " ").trim() ?? "";
  return text === "" ? null : text;
}

/**
 * The text of a chip is hidden, so the caret has no place inside it: Backspace and Delete take the whole variable, and
 * an arrow steps from one side of it to the other, as over a single character.
 */
function stepOverChip(view: EditorView, event: KeyboardEvent): boolean {
  if (event.shiftKey || event.metaKey || event.ctrlKey || event.altKey) return false;
  const { selection } = view.state;
  if (!selection.empty) return false;
  const at = selection.from;
  const ranges = KEY.getState(view.state)?.ranges ?? [];
  const arrive = (target: number) => {
    view.dispatch(view.state.tr.setSelection(TextSelection.create(view.state.doc, target)).scrollIntoView());
    return true;
  };
  if (event.key === "Backspace") {
    const before = ranges.find((range) => range.to === at);
    if (!before) return false;
    view.dispatch(view.state.tr.delete(before.from, before.to));
    return true;
  }
  if (event.key === "Delete") {
    const after = ranges.find((range) => range.from === at);
    if (!after) return false;
    view.dispatch(view.state.tr.delete(after.from, after.to));
    return true;
  }
  if (event.key === "ArrowLeft") {
    const before = ranges.find((range) => range.to === at);
    return before ? arrive(before.from) : false;
  }
  if (event.key === "ArrowRight") {
    const after = ranges.find((range) => range.from === at);
    return after ? arrive(after.to) : false;
  }
  return false;
}
