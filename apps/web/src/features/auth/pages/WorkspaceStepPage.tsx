import { useNavigate } from "react-router-dom";
import { AuthLogo, Card } from "../../../components/ui";
import { OrganisationForm } from "../../workspace/components/OrganisationForm";
import { useAuth } from "../AuthProvider";
import { SIGNUP_STEPS, Stepper } from "../components/Stepper";
import * as authApi from "../api/authApi";

/**
 * Signup step 3 — creating your workspace.
 *
 * Signing up *is* creating a workspace; membership of an existing one is invitation-only, so there is
 * no domain lookup and no join fork here. A colleague whose firm is already on LightMove asks their
 * admin for an invitation — the admin reaching out is the decision that admits them.
 */
export function WorkspaceStepPage() {
  const { user, reload } = useAuth();
  const navigate = useNavigate();

  // Already made one, and came back — via the Back button on the invite step, or by reopening the tab. This step
  // *commits*, unlike the mockup's wizard, so returning to it cannot mean "create": it means "correct
  // what you created". (A further workspace is founded from Settings → Workspaces, never from here.)
  const existing = user?.workspace ?? null;

  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-6 p-4 sm:p-6">
      <AuthLogo />
      <Stepper steps={SIGNUP_STEPS} current={3} />

      <Card className="w-[480px] max-w-[94vw] [animation-delay:80ms]">
        <OrganisationForm
          editing={existing}
          subtitle={`Step 3 of 4 · ${existing ? "update your workspace" : "this becomes your workspace"}`}
          submit={existing ? authApi.updateWorkspace : authApi.createWorkspace}
          onDone={async () => {
            await reload();
            navigate("/signup/invite", { replace: true });
          }}
        />
      </Card>
    </div>
  );
}
