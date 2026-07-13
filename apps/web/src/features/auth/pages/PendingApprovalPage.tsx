import { Button, Card, Logo } from "../../../components/ui";
import { useAuth } from "../AuthProvider";

/**
 * Where someone waits after asking to join a workspace.
 *
 * They have an account and no access to anything, and that is not a bug to be smoothed over — it is
 * the join-approval model working. So the screen says plainly what is happening and who is deciding,
 * rather than dressing a locked door up as a loading spinner.
 */
export function PendingApprovalPage() {
  const { user, signOut, reload } = useAuth();

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 p-6">
      <Logo />

      <Card className="w-[420px] max-w-[94vw] text-center [animation-delay:60ms]">
        <div className="mx-auto mb-4 grid size-11 place-items-center rounded-full bg-amber-dim">
          <svg className="size-5 text-amber" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
            <circle cx="12" cy="12" r="9" />
            <path d="M12 7v5l3 2" />
          </svg>
        </div>

        <h1 className="text-[19px] font-semibold">Waiting for approval</h1>

        <p className="mb-6 mt-2 text-[13px] leading-relaxed text-text2">
          An administrator needs to approve your request before you can access the workspace. We'll
          email {user?.email} as soon as they do.
        </p>

        <Button variant="secondary" className="w-full" onClick={() => void reload()}>
          Check again
        </Button>

        <button
          type="button"
          onClick={() => void signOut()}
          className="mt-4 w-full text-center text-[12.5px] font-medium text-text3 hover:text-text2 hover:underline"
        >
          Sign out
        </button>
      </Card>
    </div>
  );
}
