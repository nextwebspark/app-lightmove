import type { ReactNode } from "react";
import { Navigate, Outlet, Route, Routes } from "react-router-dom";
import { NotFoundPage } from "../../app/NotFoundPage";
import { useAuth } from "../auth/AuthProvider";
import type { PlatformAction } from "../auth/api/types";
import { TemplateEditorPage } from "../templates/pages/TemplateEditorPage";
import { TemplateListPage } from "../templates/pages/TemplateListPage";
import EvalCalibrationPage from "./evals/pages/EvalCalibrationPage";
import EvalDatasetPage from "./evals/pages/EvalDatasetPage";
import EvalRunPage from "./evals/pages/EvalRunPage";
import EvalsPage from "./evals/pages/EvalsPage";
import { PlatformLayout } from "./PlatformLayout";
import { PLATFORM_HOME, visiblePlatformSections } from "./platformSections";
import PromptListPage from "./prompts/pages/PromptListPage";
import PromptPage from "./prompts/pages/PromptPage";
import VariablesPage from "./variables/pages/VariablesPage";

/** Everything under `/platform`, as one chunk nobody but its holders downloads. */
export default function PlatformRoutes() {
  return (
    <Routes>
      <Route element={<PlatformLayout />}>
        <Route index element={<PlatformLanding />} />
        <Route element={<RequirePlatformAction action="TEMPLATE_LIBRARY_MANAGE"><Outlet /></RequirePlatformAction>}>
          <Route path="templates" element={<TemplateListPage scope="library" />} />
          <Route path="templates/new" element={<TemplateEditorPage scope="library" />} />
          <Route path="templates/:code" element={<TemplateEditorPage scope="library" />} />
        </Route>
        <Route element={<RequirePlatformAction action="PLATFORM_SETTINGS_MANAGE"><Outlet /></RequirePlatformAction>}>
          <Route path="variables" element={<VariablesPage />} />
        </Route>
        <Route element={<RequirePlatformAction action="PROMPT_MANAGE"><Outlet /></RequirePlatformAction>}>
          <Route path="prompts" element={<PromptListPage />} />
          <Route path="prompts/:promptId" element={<PromptPage />} />
        </Route>
        <Route element={<RequirePlatformAction action="EVAL_RUN"><Outlet /></RequirePlatformAction>}>
          <Route path="evals" element={<EvalsPage />} />
          <Route path="evals/datasets/:datasetId" element={<EvalDatasetPage />} />
          <Route path="evals/datasets/:datasetId/calibration" element={<EvalCalibrationPage />} />
          <Route path="evals/runs/:runId" element={<EvalRunPage />} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

/** `/platform` itself: the first section this person holds the action for. */
function PlatformLanding() {
  const { user } = useAuth();
  const first = visiblePlatformSections(user)[0];
  return <Navigate to={first?.to ?? "/"} replace />;
}

function RequirePlatformAction({ action, children }: { action: PlatformAction; children: ReactNode }) {
  const { user } = useAuth();
  if (!user?.platformActions.includes(action)) return <Navigate to={PLATFORM_HOME} replace />;
  return <>{children}</>;
}
