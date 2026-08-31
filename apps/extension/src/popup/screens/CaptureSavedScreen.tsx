import { workspaceOrigin } from "../../workspaceOrigin";
import { Icon } from "../components/Icon";
import { ICONS } from "../lib/icons";

interface CaptureSavedScreenProps {
  subjectName: string;
  /** Where it landed, in the mandate's own words — the server's answer, never the button pressed. */
  landedIn: string;
  projectName: string;
  projectId: string;
  sourceUrl: string | null;
  onCaptureAnother: () => void;
  /** Undoes the write. Absent while there is nothing to undo, or once it has been undone. */
  onUndo?: () => void;
  isUndoing?: boolean;
}

/**
 * The confirmation, after a capture lands. One screen for both subjects.
 *
 * States where it went rather than only that it saved: a company already sitting in the shortlist
 * stays there when captured to the universe, so the receipt has to say what is true rather than what
 * was asked for.
 */
export function CaptureSavedScreen({
  subjectName,
  landedIn,
  projectName,
  projectId,
  sourceUrl,
  onCaptureAnother,
  onUndo,
  isUndoing,
}: CaptureSavedScreenProps) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center px-6 text-center">
      <span className="grid h-[46px] w-[46px] place-items-center rounded-full bg-green-dim text-green" aria-hidden>
        <Icon d={ICONS.check} size={20} />
      </span>
      <h2 className="mt-3.5 text-[15px] font-semibold">{subjectName} added</h2>
      <p className="mt-2 text-[12.5px] leading-[1.6] text-text2">
        Filed in <span className="font-semibold text-text">{landedIn}</span>.
      </p>

      <dl className="mt-3.5 w-full rounded-[9px] bg-panel2 px-2.5 py-2 text-left">
        <ReceiptRow label="Project" value={projectName} />
        <ReceiptRow label="Landed in" value={landedIn} />
        <ReceiptRow label="Source" value={sourceUrl ?? "Typed in the popup"} />
      </dl>

      <div className="mt-4 flex gap-2">
        <a
          href={`${workspaceOrigin}/projects/${projectId}/companies`}
          target="_blank"
          rel="noreferrer"
          className="rounded-lg border border-line px-3.5 py-2 text-[12.5px] font-semibold text-text2 hover:border-text3 hover:text-text"
        >
          View in project
        </a>
        <button
          type="button"
          onClick={onCaptureAnother}
          className="rounded-lg bg-amber-btn px-3.5 py-2 text-[12.5px] font-semibold text-on-amber"
        >
          Capture another
        </button>
      </div>

      {onUndo && (
        <button
          type="button"
          onClick={onUndo}
          disabled={isUndoing}
          className="mt-3 inline-flex items-center gap-1.5 text-[11.5px] text-sky hover:underline disabled:opacity-60"
        >
          <Icon d={ICONS.undo} size={12} />
          {isUndoing ? "Undoing…" : "Undo"}
        </button>
      )}
    </div>
  );
}

function ReceiptRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline gap-2 py-[3px]">
      <dt className="w-[68px] shrink-0 font-mono text-[9.5px] font-semibold uppercase tracking-[0.11em] text-text3">
        {label}
      </dt>
      <dd className="flex-1 truncate text-[11.5px] text-text2">{value}</dd>
    </div>
  );
}
