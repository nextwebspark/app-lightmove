import type { PositionDiscipline } from "../../position/api/types";
import type { TemplateScope } from "../api/types";

/** The groups a Templates list is sorted into, in the order it shows them. */
export const DISCIPLINE_LABELS: Record<PositionDiscipline, string> = {
  EXECUTIVE: "Executive",
  FINANCE: "Finance",
  OPERATIONS: "Operations",
  TECHNOLOGY: "Technology",
  PEOPLE: "People",
  COMMERCIAL: "Commercial",
  GOVERNANCE: "Governance",
  INVESTMENT: "Investment",
};

export const DISCIPLINES = Object.keys(DISCIPLINE_LABELS) as PositionDiscipline[];

export const TEMPLATE_LIBRARY_PATH = "/platform/templates";

export const SCOPE_COPY: Record<TemplateScope, { heading: string; path: string }> = {
  library: { heading: "Templates", path: TEMPLATE_LIBRARY_PATH },
  workspace: { heading: "Templates", path: "/settings/templates" },
};
