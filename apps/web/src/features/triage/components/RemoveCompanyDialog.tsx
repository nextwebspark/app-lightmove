import { Button, Modal } from "../../../components/ui";
import type { Candidate } from "../../candidates/api/types";
import type { TriageCompany } from "../api/types";

/**
 * Confirms removing a company from the mandate.
 *
 * <p>The copy does the work here. "Delete" beside a company name reads as deleting the company, and
 * this deletes a decision: the row that says what *this* mandate thought of it. The company stays in
 * the market, stays on Strategy, and stays untouched for every other mandate — so the dialog says
 * that rather than asking "are you sure?", which tells a hesitating reader nothing they did not
 * already know.
 *
 * <p>It also names the one thing that is genuinely lost, because the decision is not remembered:
 * a later bulk add from Strategy may take the company back in. Declining is how a company is ruled
 * out durably, and someone reaching for delete may have meant that instead.
 *
 * <p>A company somebody is mapped at is not removable, and this says so instead of offering the
 * button. The people are the reason: a company row is what a person's grid line sits on, so removing
 * it under them leaves an executive nobody can open, stage or act on. The server refuses the same
 * thing — this is the half of it a reader can act on, because it knows who is in the way by name.
 */
export function RemoveCompanyDialog({
  company,
  mappedExecutives,
  removing,
  onCancel,
  onConfirm,
}: {
  /** The company awaiting confirmation, or null when the dialog is closed. */
  company: TriageCompany | null;
  /** Everyone this mandate maps at it — one of them is enough to refuse the removal. */
  mappedExecutives: readonly Candidate[];
  removing: boolean;
  onCancel: () => void;
  onConfirm: (company: TriageCompany) => void;
}) {
  if (!company) return null;

  if (mappedExecutives.length > 0) {
    return (
      <Modal open onClose={onCancel} title={`${company.companyName} can't be removed`}>
        <p className="text-[13px]/[1.6] text-text2">
          {mappedExecutives.length === 1
            ? `${mappedExecutives[0].fullName} is mapped here.`
            : `${mappedExecutives.length} executives are mapped here.`}{" "}
          Removing the company would leave them on this grid with nothing to sit on — no stage, no
          logo, and nothing to open.
        </p>
        <p className="mt-2.5 text-[13px]/[1.6] text-text3">
          Move them to another employer from their own profile, or remove them, and this company can go
          after them. To rule the company out while keeping the people, decline it instead.
        </p>

        <div className="mt-5 flex justify-end">
          <Button variant="secondary" onClick={onCancel}>
            Close
          </Button>
        </div>
      </Modal>
    );
  }

  return (
    <Modal open onClose={onCancel} title={`Remove ${company.companyName}?`}>
      <p className="text-[13px]/[1.6] text-text2">
        This removes {company.companyName} from this mandate — the stage it reached and any note on it
        go with it.
      </p>
      <p className="mt-2.5 text-[13px]/[1.6] text-text2">
        The company itself is not deleted. It stays in the market, findable on Strategy, and untouched
        for every other mandate.
      </p>
      <p className="mt-2.5 text-[13px]/[1.6] text-text3">
        Removing is not remembered, so a later bulk add from Strategy could bring it back. To rule it
        out for good, decline it instead.
      </p>

      <div className="mt-5 flex justify-end gap-2">
        <Button variant="secondary" onClick={onCancel} disabled={removing}>
          Cancel
        </Button>
        <Button
          variant="primary"
          loading={removing}
          onClick={() => onConfirm(company)}
        >
          Remove from mandate
        </Button>
      </div>
    </Modal>
  );
}
