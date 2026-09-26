import { useNavigate } from "react-router-dom";
import { AuthLogo, Button, Card } from "../../../components/ui";
import { InviteTeamForm } from "../../workspace/components/InviteTeamForm";
import { useAuth } from "../AuthProvider";
import { homeFor } from "../homeFor";
import { SIGNUP_STEPS, Stepper } from "../components/Stepper";
import * as authApi from "../api/authApi";

/**
 * Signup step 4 — "Invite your team". A port of Signup.dc.html's final step.
 *
 * Optional, and the mockup's "Skip for now" is honoured exactly: it goes to the workspace without
 * calling the API at all.
 */
export function InviteStepPage() {
  const navigate = useNavigate();
  const { user, reload } = useAuth();

  // Reaching this step means verified with a workspace, so "/" is where the wizard ends. homeFor
  // covers the one case left: a tab left open while the workspace was abandoned elsewhere.
  const done = user?.workspace ? "/" : homeFor(user);

  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-6 p-4 sm:p-6">
      <AuthLogo />

      {/* Organization only. The account and the verification are done and cannot be redone. */}
      <Stepper
        steps={SIGNUP_STEPS}
        current={4}
        backableSteps={[3]}
        onGoBack={() => navigate("/signup/workspace")}
      />

      <Card className="w-[480px] max-w-[94vw] [animation-delay:80ms]">
        <InviteTeamForm
          subtitle="Step 4 of 4 · optional — invite people later from Team"
          submit={authApi.invite}
          onDone={async () => {
            await reload();
            navigate(done, { replace: true });
          }}
          onSkip={() => navigate("/", { replace: true })}
          // Back beside Continue, as the mockup has it. Safe because step 3 edits the workspace it
          // already created rather than trying to create a second one.
          before={(submitting) => (
            <Button
              variant="secondary"
              className="shrink-0"
              disabled={submitting}
              onClick={() => navigate("/signup/workspace")}
            >
              Back
            </Button>
          )}
        />
      </Card>
    </div>
  );
}
