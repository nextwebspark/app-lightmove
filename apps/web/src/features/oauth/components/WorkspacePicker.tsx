import { useRadioGroupKeys } from "../../../components/ui/useRadioGroupKeys";
import { cn } from "../../../lib/cn";
import type { ConsentWorkspace } from "../api/types";

/** The workspace the grant is for: named alone when there is one, a radio group when there are several. */
export function WorkspacePicker({
  workspaces,
  value,
  onChange,
}: {
  workspaces: ConsentWorkspace[];
  value: string;
  onChange: (workspaceId: string) => void;
}) {
  const radioKeys = useRadioGroupKeys(
    workspaces.map(({ id }) => id),
    value,
    onChange,
  );

  if (workspaces.length === 1) {
    return (
      <div className="flex items-center gap-2.5 rounded-lg border border-u-border bg-u-raised px-3 py-2.5">
        <WorkspaceMark name={workspaces[0].name} />
        <span className="text-[13px] font-medium text-u-text">{workspaces[0].name}</span>
      </div>
    );
  }

  return (
    <>
      <div
        role="radiogroup"
        aria-label="Workspace"
        ref={radioKeys.ref}
        onKeyDown={radioKeys.onKeyDown}
        className="rounded-lg border border-u-border bg-u-raised"
      >
        {workspaces.map((option, index) => {
          const isChosen = option.id === value;
          return (
            <button
              key={option.id}
              type="button"
              role="radio"
              aria-checked={isChosen}
              tabIndex={isChosen ? 0 : -1}
              onClick={() => onChange(option.id)}
              className={cn(
                "flex w-full items-center gap-2.5 px-3 py-2.5 text-start hover:bg-u-surface",
                index > 0 && "border-t border-u-border",
              )}
            >
              <span
                className={cn(
                  "size-3.5 flex-none rounded-full bg-u-surface",
                  isChosen ? "border-4 border-u-accent-solid" : "border border-u-border-strong",
                )}
              />
              <WorkspaceMark name={option.name} />
              <span className="min-w-0 flex-1 text-[13px] font-medium text-u-text">{option.name}</span>
            </button>
          );
        })}
      </div>
      <span className="mt-1.5 block font-mono text-[11px] leading-[1.5] text-u-text3">
        One workspace per connection. Connect again to add another.
      </span>
    </>
  );
}

function WorkspaceMark({ name }: { name: string }) {
  return (
    <span
      aria-hidden="true"
      className="grid size-[22px] flex-none place-items-center rounded-[5px] bg-u-accent-solid font-mono text-[10px] font-bold text-white"
    >
      {name.trim().charAt(0).toUpperCase()}
    </span>
  );
}
