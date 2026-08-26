import { Button, Modal } from "../../../components/ui";
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
 */
export function RemoveCompanyDialog({
  company,
  removing,
  onCancel,
  onConfirm,
}: {
  /** The company awaiting confirmation, or null when the dialog is closed. */
  company: TriageCompany | null;
  removing: boolean;
  onCancel: () => void;
  onConfirm: (company: TriageCompany) => void;
}) {
  if (!company) return null;

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
