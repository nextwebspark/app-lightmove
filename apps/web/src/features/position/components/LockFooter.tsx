import { Button } from "../../../components/ui";
import type { Readiness } from "../lib/readiness";

/**
 * The lock gate. Unlocked: a live checklist of the three readiness rules and the Lock button,
 * enabled only when all pass. Locked: the green banner, with Unlock offered to admins only (the
 * server enforces POSITION_UNLOCK regardless).
 */
export function LockFooter({
  locked,
  readiness,
  canUnlock,
  locking,
  onLock,
  onUnlock,
}: {
  locked: boolean;
  readiness: Readiness;
  canUnlock: boolean;
  locking: boolean;
  onLock: () => void;
  onUnlock: () => void;
}) {
  if (locked) {
    return (
      <div className="mt-5 flex items-center gap-3.5 rounded-[10px] border border-green bg-green-dim px-[18px] py-3.5">
        <div className="flex-1">
          <div className="text-[13px] font-semibold text-green">Position locked</div>
          <div className="mt-0.5 font-mono text-[11.5px] text-text3">
            Criteria and weighting are the reference for candidate benchmarking downstream.
          </div>
        </div>
        {canUnlock && (
          <Button variant="secondary" onClick={onUnlock}>
            Unlock
          </Button>
        )}
      </div>
    );
  }

  return (
    <div className="mt-5 flex items-center gap-3.5 rounded-[10px] border border-line-soft bg-panel2 px-[18px] py-3.5">
      <div className="flex-1">
        <div className="mb-1.5 text-[13px] font-semibold">Lock position</div>
        <ChecklistRow
          done={readiness.technicalTotal === 100}
          label={`Technical weights total 100% (currently ${readiness.technicalTotal}%)`}
        />
        <ChecklistRow
          done={readiness.behaviouralTotal === 100}
          label={`Behavioural weights total 100% (currently ${readiness.behaviouralTotal}%)`}
        />
        <ChecklistRow done={readiness.hasRequired} label="At least one Required criterion" />
      </div>
      <Button onClick={onLock} disabled={!readiness.ready} loading={locking}>
        Lock position
      </Button>
    </div>
  );
}

function ChecklistRow({ done, label }: { done: boolean; label: string }) {
  return (
    <div
      className={`flex items-center gap-1.5 py-px font-mono text-[11.5px] ${
        done ? "text-green" : "text-text3"
      }`}
    >
      <span aria-hidden="true">{done ? "✓" : "✗"}</span>
      {label}
    </div>
  );
}
