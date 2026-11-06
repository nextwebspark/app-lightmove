import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import type { TriageCompany } from "../api/types";

/** Asked before declining a company with people mapped at it: they are declined with it, and come back with it. */
export function DeclineCompanyDialog({
  company,
  executiveCount,
  declining,
  onCancel,
  onConfirm,
}: {
  /** The company awaiting confirmation, or null when the dialog is closed. */
  company: TriageCompany | null;
  executiveCount: number;
  declining: boolean;
  onCancel: () => void;
  onConfirm: (company: TriageCompany) => void;
}) {
  if (!company) return null;
  const executives = executiveCount === 1 ? "1 executive" : `${executiveCount} executives`;

  return (
    <ConfirmDialog
      open
      title={`Decline ${company.companyName}?`}
      confirmLabel="Decline company"
      pending={declining}
      onConfirm={() => onConfirm(company)}
      onClose={onCancel}
    >
      <p>
        <span className="font-medium text-u-text">
          {company.companyName} has {executives}. {executiveCount === 1 ? "They are" : "All of them are"} declined
          with it.
        </span>
      </p>
      <p>Back to universe brings the company and everyone at it back.</p>
    </ConfirmDialog>
  );
}
