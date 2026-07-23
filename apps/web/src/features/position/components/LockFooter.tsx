import { useNavigate } from "react-router-dom";

/**
 * Closes the Position screen with a way forward. Was a lock/unlock gate (readiness checklist +
 * Lock/Unlock buttons) — the brief still locks server-side (POSITION_LOCKED, POSITION_UNLOCK), but
 * this screen no longer offers a control for it; it only points on to Strategy.
 */
export function LockFooter({ projectId }: { projectId: string }) {
  const navigate = useNavigate();

  return (
    <button
      type="button"
      onClick={() => navigate(`/projects/${projectId}/strategy`)}
      className="mt-5 flex w-full items-center justify-between gap-3.5 rounded-[10px] border border-line-soft bg-panel2 px-[18px] py-3.5 text-left transition hover:border-text3"
    >
      <div>
        <div className="text-[13px] font-semibold text-text">Go to Strategy</div>
        <div className="mt-0.5 font-mono text-[11.5px] text-text3">
          Scope the sectors, company size, and geography this mandate searches against.
        </div>
      </div>
      <span aria-hidden="true" className="flex-none font-mono text-[13px] text-text3">
        →
      </span>
    </button>
  );
}
