import type { ActivePage } from "../hooks/useActivePage";
import type { ProjectSelection } from "../hooks/useProjectSelection";

/** What both capture tabs are handed: one read of the page, and one choice of mandate. */
export interface CaptureScreenProps {
  page: ActivePage;
  projects: ProjectSelection;
}
