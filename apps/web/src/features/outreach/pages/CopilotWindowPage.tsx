import { useParams } from "react-router-dom";
import { CopilotPanel } from "../components/copilot/CopilotPanel";

/**
 * The copilot as a page of its own, for the popup a browser without floating windows opens: no app chrome, the panel
 * filling the window. It does not stay on top of the call, so it says so once.
 */
export default function CopilotWindowPage() {
  const { projectId = "", candidateId = "", recordingId = "" } = useParams();
  return (
    <div className="h-dvh bg-u-surface">
      <CopilotPanel
        projectId={projectId}
        candidateId={candidateId}
        recordingId={recordingId}
        variant="window"
        onClose={() => window.close()}
        notice="This window won't stay on top in this browser. Keep it beside the call."
      />
    </div>
  );
}
