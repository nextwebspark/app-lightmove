import type { Editor } from "@tiptap/core";
import { Selection, TextSelection } from "@tiptap/pm/state";
import { act } from "@testing-library/react";

/**
 * Types into a note editor the way ProseMirror reads a keystroke, so input rules (`- `, `## `, `[] `) and the @
 * picker fire. `userEvent.type` cannot: jsdom keeps no caret ProseMirror can read back, and the letters land out of
 * order.
 */
export async function typeInto(box: HTMLElement, text: string) {
  const editor = editorOf(box);
  if (!editor.isFocused) {
    await act(async () => {
      editor.commands.focus("end");
    });
  }
  for (const character of text) {
    await act(async () => {
      const { view } = editor;
      // jsdom's own selection sits outside the editor, which ProseMirror reads as the whole document selected.
      if (!view.state.selection.empty) {
        const end = Selection.atEnd(view.state.doc).from;
        view.dispatch(view.state.tr.setSelection(TextSelection.create(view.state.doc, end)));
      }
      const { from, to } = view.state.selection;
      const handled = view.someProp("handleTextInput", (handle) =>
        handle(view, from, to, character, () => view.state.tr.insertText(character, from, to)),
      );
      if (!handled) view.dispatch(view.state.tr.insertText(character, from, to));
    });
  }
}

export function editorOf(box: HTMLElement): Editor {
  const editor = (box as HTMLElement & { editor?: Editor }).editor;
  if (!editor) throw new Error("not a note editor");
  return editor;
}
