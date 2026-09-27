import { ChoiceCardGroup } from "../../../components/ui";
import type { WorkspaceMode } from "../../auth/api/types";
import { WORKSPACE_MODE_OPTIONS } from "../lib/workspaceModes";

/** Who a workspace hires for. Null until chosen: the question has no default. */
export function WorkspaceModeChoice({
  value,
  onChange,
  error,
}: {
  value: WorkspaceMode | null;
  onChange: (mode: WorkspaceMode) => void;
  error?: string;
}) {
  return (
    <div className="mb-4">
      <span className="mb-1.5 block font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-u-text3">Who do you hire for?</span>
      <ChoiceCardGroup
        label="Who do you hire for?"
        options={WORKSPACE_MODE_OPTIONS}
        value={value}
        onChange={onChange}
        invalid={!!error}
      />
      <span aria-live="polite">
        {error && <span className="mt-1.5 block font-mono text-[11px] text-u-offlimits">{error}</span>}
      </span>
    </div>
  );
}
