import { useNavigate } from "react-router-dom";
import { AuthLogo, Card } from "../../../components/ui";
import { OrganisationForm } from "../../workspace/components/OrganisationForm";
import { useAuth } from "../AuthProvider";
import { SIGNUP_STEPS, Stepper } from "../components/Stepper";
import * as authApi from "../api/authApi";

/**
 * Signup step 3, the last: creating your workspace. Membership of an existing one is invitation-only, so there is
 * no domain lookup or join fork here.
 */
export function WorkspaceStepPage() {
  const { user, reload } = useAuth();
  const navigate = useNavigate();

  // Already made one, and came back by reopening the tab. This step
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
          subtitle={`Step 3 of 3 · ${existing ? "update your workspace" : "this becomes your workspace"}`}
          submit={existing ? authApi.updateWorkspace : authApi.createWorkspace}
          onDone={async () => {
            await reload();
            navigate("/", { replace: true });
          }}
        />
      </Card>
    </div>
  );
}
