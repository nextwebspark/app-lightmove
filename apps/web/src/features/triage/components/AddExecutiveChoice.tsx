import type { ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Popover } from "../../../components/ui/Popover";
import type { TriageCompany } from "../api/types";

/**
 * A company's empty slot where Find executives is offered: its button opens two ways in, beside it — Uncava's own
 * search of that company alone, or the form to type someone in.
 */
export function AddExecutiveChoice({
  company,
  label,
  title,
  triggerClassName,
  trigger,
  onFind,
  onAddByHand,
}: {
  company: TriageCompany;
  label: string;
  title: string;
  triggerClassName: string;
  trigger: ReactNode;
  onFind: (company: TriageCompany) => void;
  onAddByHand: (company: TriageCompany) => void;
}) {
  return (
    <Popover
      label={label}
      title={title}
      hasPopup="menu"
      placement="auto"
      width={272}
      triggerClassName={triggerClassName}
      trigger={() => trigger}
    >
      {(close) => (
        <div role="menu" aria-label={`Add an executive at ${company.companyName}`} className="flex flex-col">
          <Choice
            icon={ICONS.search}
            label="Find with Uncava"
            hint="Search this company and review who fits"
            onSelect={() => {
              close();
              onFind(company);
            }}
          />
          <Choice
            icon={ICONS.userPlus}
            label="Add manually"
            hint="Type in someone you already know"
            onSelect={() => {
              close();
              onAddByHand(company);
            }}
          />
        </div>
      )}
    </Popover>
  );
}

function Choice({ icon, label, hint, onSelect }: { icon: string; label: string; hint: string; onSelect: () => void }) {
  return (
    <button
      type="button"
      role="menuitem"
      onClick={onSelect}
      className="flex w-full items-start gap-2.5 rounded-md px-2 py-2 text-left hover:bg-u-raised"
    >
      <Icon d={icon} size={15} className="mt-0.5 flex-none text-u-accent" />
      <span className="flex min-w-0 flex-col">
        <span className="text-body font-medium text-u-text">{label}</span>
        <span className="text-note text-u-text3">{hint}</span>
      </span>
    </button>
  );
}
