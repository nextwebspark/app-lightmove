import { CheckBox } from "../../../components/ui/FilterCheckRow";
import { cn } from "../../../lib/cn";
import type { ApiKeyScope } from "../../settings/api/types";
import { CONSENT_SCOPES } from "../lib/consentRequest";

/** The scopes the app asked for, one checkbox row each; personal data says so. */
export function ScopeChoices({
  requested,
  ticked,
  onToggle,
}: {
  requested: readonly ApiKeyScope[];
  ticked: readonly ApiKeyScope[];
  onToggle: (scope: ApiKeyScope) => void;
}) {
  const offered = CONSENT_SCOPES.filter(({ scope }) => requested.includes(scope));
  return (
    <div className="rounded-lg border border-u-border bg-u-raised">
      {offered.map(({ scope, label, note, personalData }, index) => {
        const isTicked = ticked.includes(scope);
        return (
          <button
            key={scope}
            type="button"
            role="checkbox"
            aria-checked={isTicked}
            onClick={() => onToggle(scope)}
            className={cn(
              "flex w-full items-start gap-2.5 px-3 py-2.5 text-start hover:bg-u-surface",
              index > 0 && "border-t border-u-border",
            )}
          >
            <span className="mt-0.5">
              <CheckBox checked={isTicked} />
            </span>
            <span className="min-w-0 flex-1">
              <span className="flex flex-wrap items-center gap-1.5">
                <span className="text-[13px] font-medium text-u-text">{label}</span>
                {personalData && (
                  <span className="rounded-full bg-u-offlimits-tint px-[7px] py-px font-mono text-[9px] font-semibold uppercase tracking-[0.06em] text-u-offlimits">
                    Personal data
                  </span>
                )}
              </span>
              <span className="mt-0.5 block font-mono text-[11px] leading-[1.5] text-u-text3">{note}</span>
            </span>
          </button>
        );
      })}
    </div>
  );
}
