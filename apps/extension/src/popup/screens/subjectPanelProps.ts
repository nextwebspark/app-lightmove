import type { WorkspaceUser } from "../../api/types";
import type { ActivePage } from "../hooks/useActivePage";
import type { ProjectSelection } from "../hooks/useProjectSelection";

/** What the person and company panels are handed: one read of the page, the chosen position, and who is paired. */
export interface SubjectPanelProps {
  page: ActivePage;
  projects: ProjectSelection;
  user: WorkspaceUser;
}
