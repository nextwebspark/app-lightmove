import type { ChoiceCardOption } from "../../../components/ui";
import { WORKSPACE_MODES, type WorkspaceMode } from "../../auth/api/types";

const COPY: Record<WorkspaceMode, { title: string; body: string }> = {
  AGENCY: {
    title: "Search firm",
    body: "You run searches for client companies.",
  },
  COMPANY: {
    title: "In-house talent team",
    body: "You hire for your own company's business units.",
  },
};

/** The two answers to "who do you hire for", as the organisation step and Settings → General draw them. */
export const WORKSPACE_MODE_OPTIONS: readonly ChoiceCardOption<WorkspaceMode>[] = WORKSPACE_MODES.map((mode) => ({
  value: mode,
  ...COPY[mode],
}));
