import { Icon, ICONS } from "../../../components/layout/Icon";
import { Spinner } from "../../../components/ui";
import { useAuth } from "../../auth/AuthProvider";
import { useFeedback } from "../FeedbackProvider";

/**
 * The floating trigger for the screens that have no sidebar to put one in — login, signup, the
 * verification and invitation pages, and the two onboarding steps.
 *
 * <p>Hidden the moment a workspace exists, because from there on `AppShell` is mounted and the rail
 * carries the row instead. Two triggers on one screen is one too many, and the rail's is the one a
 * tester will find without being told.
 */
export function FeedbackLauncher() {
  const { user, loading } = useAuth();
  const { open, isCapturing } = useFeedback();

  // The shell mounts under RequireWorkspace, so "has a workspace" is exactly "the rail is on screen".
  // Also held back while the session restores, so it does not flash in and out on a hard refresh.
  if (loading || user?.workspace) return null;

  return (
    <button
      type="button"
      onClick={open}
      disabled={isCapturing}
      title="Report a bug or request a feature"
      className={
        "fixed end-0 top-1/2 z-[80] flex -translate-y-1/2 items-center gap-2 rounded-s-lg border " +
        "border-e-0 border-line bg-panel px-2.5 py-3 text-text2 shadow-panel transition " +
        "hover:text-text disabled:cursor-wait sm:px-3"
      }
    >
      {isCapturing ? <Spinner /> : <Icon d={ICONS.warning} size={15} />}
      {/* Upright on a desktop, where there is room at the edge; icon-only on a phone, where a
          vertical word down the side of a 390px viewport is just a thing in the way. */}
      <span className="hidden font-mono text-[11px] font-semibold uppercase tracking-[0.12em] [writing-mode:vertical-rl] sm:block">
        Report
      </span>
    </button>
  );
}
