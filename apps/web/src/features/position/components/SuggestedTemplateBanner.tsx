import { Icon, ICONS } from "../../../components/layout/Icon";
import type { PositionTemplate } from "../api/types";
import { BriefButton, BriefPanel } from "./BriefFields";

/**
 * The offer a document reading makes when the role it describes matches a template the brief was not
 * already drafted from: a one-line banner under the file card, never applied automatically. Applying
 * drafts the brief from the template first and re-reads the document after, so the reading's own
 * values still win over whatever the template seeded.
 */
export function SuggestedTemplateBanner({
  template,
  applying,
  onApply,
  onDismiss,
}: {
  template: PositionTemplate;
  applying: boolean;
  onApply: () => void;
  onDismiss: () => void;
}) {
  return (
    <BriefPanel className="flex flex-wrap items-center gap-3 border border-u-inferred/25 bg-u-inferred-tint px-4 py-3 text-body text-u-text">
      <Icon d={ICONS.sparkle} size={14} className="flex-none text-u-inferred" />
      <span className="min-w-0 flex-1">
        This reads like a <b className="font-semibold">{template.title}</b> mandate — draft from that template too?
      </span>
      <BriefButton variant="outline" loading={applying} onClick={onApply} className="flex-none">
        Apply
      </BriefButton>
      <button
        type="button"
        aria-label="Dismiss"
        onClick={onDismiss}
        className="flex-none text-u-text3 transition hover:text-u-text"
      >
        <Icon d={ICONS.close} size={13} />
      </button>
    </BriefPanel>
  );
}
