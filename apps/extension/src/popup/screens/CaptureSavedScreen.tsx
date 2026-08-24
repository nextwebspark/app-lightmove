import type { TriagedCompany } from "../../api/types";
import { DESTINATION_PAST_TENSE, type TriageDestination } from "../../domain/triageDestination";
import { workspaceOrigin } from "../../workspaceOrigin";

interface CaptureSavedScreenProps {
  saved: TriagedCompany;
  projectId: string;
  destination: TriageDestination;
  onCaptureAnother: () => void;
}

/**
 * The confirmation, after a company lands.
 *
 * States where it went in the mandate's own words, because "saved" alone leaves the consultant to
 * remember which of the two buttons they pressed. `status` comes from the server rather than from the
 * button: a company already sitting in the shortlist stays there when captured to the universe, and
 * the receipt has to say what is true rather than what was asked for.
 */
export function CaptureSavedScreen({
  saved,
  projectId,
  destination,
  onCaptureAnother,
}: CaptureSavedScreenProps) {
  const landedIn = saved.status === "declined" ? null : DESTINATION_PAST_TENSE[saved.status];
  const asked = DESTINATION_PAST_TENSE[destination];

  return (
    <div className="flex flex-1 flex-col items-center justify-center px-6 text-center">
      <span className="grid h-[46px] w-[46px] place-items-center rounded-full bg-green-dim text-[20px] text-green" aria-hidden>
        ✓
      </span>
      <h2 className="mt-3.5 text-[15px] font-semibold">{saved.companyName} added</h2>
      <p className="mt-2 text-[12.5px] leading-[1.6] text-text2">
        It is in <span className="font-semibold text-text">{landedIn ?? asked}</span> for this mandate.
        {saved.origin === "CAPTURE" && " Captured from this page — it is not in the Apollo universe."}
      </p>

      <div className="mt-4 flex gap-2">
        <a
          href={`${workspaceOrigin}/projects/${projectId}/triage`}
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
    </div>
  );
}
