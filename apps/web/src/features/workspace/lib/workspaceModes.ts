import type { ChoiceCardOption } from "../../../components/ui";
import type { WorkspaceMode } from "../../auth/api/types";

/** The two answers to "who do you hire for", as the organisation step and Settings → General draw them. */
export const WORKSPACE_MODE_OPTIONS: readonly ChoiceCardOption<WorkspaceMode>[] = [
  {
    value: "AGENCY",
    title: "Search agency",
    body: "You hire for client companies — each client is a separate business.",
  },
  {
    value: "COMPANY",
    title: "In-house team",
    body: "You hire for your own organisation — each business unit is a client.",
  },
];
