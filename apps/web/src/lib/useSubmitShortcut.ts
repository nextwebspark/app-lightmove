import type { KeyboardEvent } from "react";

/**
 * Ctrl/⌘-Enter triggers `onSubmit`; plain Enter is left alone so a multi-line textarea keeps
 * inserting newlines rather than submitting. Shared by every free-text field that saves on its own —
 * a note, an inline-edit grid cell — and by the profile's section forms, which pass their own
 * `requestSubmit()` as the callback.
 */
export function useSubmitShortcut(onSubmit: () => void) {
  return (event: KeyboardEvent<HTMLElement>) => {
    if (event.key === "Enter" && (event.metaKey || event.ctrlKey)) {
      event.preventDefault();
      onSubmit();
    }
  };
}
