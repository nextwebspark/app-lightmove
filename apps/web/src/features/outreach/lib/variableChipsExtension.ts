import { Extension, type Editor } from "@tiptap/core";
import { Plugin, PluginKey, TextSelection, type EditorState } from "@tiptap/pm/state";
import { Decoration, DecorationSet, type EditorView } from "@tiptap/pm/view";
import { TOKEN_PATTERN } from "./sequenceTokens";
import { CHIP_CLASS, type VariableChip, type VariableChipKind } from "./variableChips";

/** What the chips need from the composer, read at draw time so the editor is built once. */
export interface VariableChipHost {
  /** Null while the executive's values load, or for a name no email fills: the text stays as typed. */
  chipFor: (name: string, fallback: string | null) => VariableChip | null;
  onChip: (target: { name: string; kind: VariableChipKind; chip: VariableChip; rect: DOMRect }) => void;
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

/**
 * An email to one person reads each `{{variable}}` as that person's value in a chip, and stays the variable underneath:
 * the words in the document and in the stored Markdown are untouched (a template saved from the email keeps
 * `{{firstName}}`, not a name), and only what is drawn changes. The raw text is hidden and the caret steps over it as one.
 */
export function variableChipsExtension(host: { current: VariableChipHost | null }) {
  return Extension.create({
    name: "variableChips",
    addProseMirrorPlugins: () => [
      new Plugin<ChipState>({
        key: KEY,
        state: {
          init: (_config, state) => chipsOf(state, host),
          apply: (transaction, previous, _old, state) =>
            transaction.docChanged || transaction.getMeta(REFRESH) ? chipsOf(state, host) : previous,
        },
        props: {
          decorations: (state) => KEY.getState(state)?.decorations,
          handleKeyDown: (view, event) => stepOverChip(view, event),
        },
      }),
    ],
  });
}

/** Draws the chips again, once the executive's values or the composer's rules have changed under the same words. */
export function refreshVariableChips(editor: Editor | null | undefined) {
  if (!editor || editor.isDestroyed) return;
  editor.view.dispatch(editor.state.tr.setMeta(REFRESH, true));
}

/** Every use of one variable in the words replaced by `replacement` ("" takes it out), as one change. */
export function replaceVariableInEditor(editor: Editor, name: string, replacement: string) {
  const edits: Array<{ from: number; to: number }> = [];
  editor.state.doc.descendants((node, position) => {
    if (!node.isText || !node.text) return;
    for (const match of node.text.matchAll(new RegExp(TOKEN_PATTERN.source, "g"))) {
      if (match[1] !== name) continue;
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
          Decoration.widget(from, () => chipElement(chip, name, source), {
            side: -1,
            key: `${from}:${name}:${chip.kind}:${chip.text}`,
            // A press on a chip opens its popover; it must not also move the caret.
            stopEvent: (event) => event.type === "mousedown" || event.type === "click",
          }),
          Decoration.inline(from, to, { style: "display:none" }),
        );
      }
    });
  }
  return { decorations: DecorationSet.create(state.doc, decorations), ranges };
}

function chipElement(chip: VariableChip, name: string, host: VariableChipHost): HTMLElement {
  const element = document.createElement("span");
  element.contentEditable = "false";
  element.className = CHIP_CLASS[chip.kind];
  element.dataset.variable = name;
  element.dataset.chip = chip.kind;
  element.title = chip.title;
  element.setAttribute("role", "button");
  element.setAttribute("aria-label", `${chip.label}: ${chip.kind === "blank" ? "nothing on file" : chip.text}`);
  element.textContent = chip.text;
  element.addEventListener("click", (event) => {
    event.preventDefault();
    host.onChip({ name, kind: chip.kind, chip, rect: element.getBoundingClientRect() });
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
