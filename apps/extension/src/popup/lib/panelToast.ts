import { createContext, useContext } from "react";

/** Shows the panel's toast, with Undo where the action can be taken back. */
export const PanelToastContext = createContext<(text: string, undo?: () => void) => void>(() => undefined);

export function usePanelToast() {
  return useContext(PanelToastContext);
}
