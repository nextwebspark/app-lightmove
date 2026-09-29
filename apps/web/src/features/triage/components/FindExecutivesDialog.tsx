import { Button, Modal } from "../../../components/ui";
import type { SourcingConfig } from "../api/sourcingApi";
import { companiesOf } from "../lib/sourcingSummary";

/** Confirms a Find executives run in the user's own numbers before anything is bought. */
export function FindExecutivesDialog({
  open,
  config,
  selectedCount,
  starting,
  onCancel,
  onConfirm,
}: {
  open: boolean;
  config: SourcingConfig;
  /** How many companies are ticked; zero means the server picks. */
  selectedCount: number;
  starting: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const companies = companiesOf(selectedCount > 0 ? selectedCount : config.maxCompaniesPerRun);

  return (
    <Modal open={open} onClose={onCancel} title="Find executives?">
      <p className="text-body text-u-text2">
        {selectedCount > 0
          ? `Uncava will search the ${companies} you ticked for people whose current title fits this position.`
          : `Uncava will search the first ${companies} in the universe with no executive mapped yet.`}{" "}
        It buys up to {config.hitsPerCompany} profiles per company and files the{" "}
        {config.picksPerCompany} that fit best, researched, as executives.
      </p>
      {selectedCount === 0 && (
        <p className="mt-2.5 text-body text-u-text3">
          To choose the companies yourself, cancel and tick them first — up to {config.maxCompaniesPerRun} at a time.
        </p>
      )}
      <p className="mt-2.5 text-body text-u-text3">
        The run continues in the background; you can keep working and the executives appear as they
        are found. Anyone already mapped here is never added twice.
      </p>

      <div className="mt-5 flex justify-end gap-2">
        <Button variant="secondary" onClick={onCancel} disabled={starting}>
          Cancel
        </Button>
        <Button variant="primary" loading={starting} onClick={onConfirm}>
          Find executives
        </Button>
      </div>
    </Modal>
  );
}
