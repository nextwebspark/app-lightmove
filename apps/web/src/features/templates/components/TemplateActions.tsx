import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import type { TemplateOverview, TemplateScope } from "../api/types";
import { toggleLabelOf } from "../lib/filtering";
import { SCOPE_COPY } from "../lib/labels";

/** A template row's Hide/Show (or Archive/Restore) and Open, shared by the grid and the phone cards. */
export function TemplateActions({
  scope,
  template,
  isToggling,
  onToggle,
}: {
  scope: TemplateScope;
  template: TemplateOverview;
  isToggling: boolean;
  onToggle: (template: TemplateOverview) => void;
}) {
  const toggleLabel = toggleLabelOf(scope, template);
  return (
    <span className="flex items-center justify-end gap-1.5">
      {toggleLabel && (
        <button
          type="button"
          aria-label={`${toggleLabel} ${template.title}`}
          disabled={isToggling}
          onClick={() => onToggle(template)}
          className="rounded-md px-2 py-1 text-xs font-medium text-u-text3 transition hover:bg-u-raised hover:text-u-text disabled:opacity-50"
        >
          {toggleLabel}
        </button>
      )}
      <Link
        to={`${SCOPE_COPY[scope].path}/${encodeURIComponent(template.code)}`}
        aria-label={`Open ${template.title}`}
        className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-[7px] border border-u-border-strong px-[11px] py-[5px] text-xs font-semibold text-u-text2 transition hover:border-u-text3 hover:bg-u-surface hover:text-u-text"
      >
        Open
        <Icon d={ICONS.arrowRight} size={13} />
      </Link>
    </span>
  );
}
