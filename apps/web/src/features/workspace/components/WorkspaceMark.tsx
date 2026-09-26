import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import type { WorkspaceSummary } from "../../auth/api/types";

/** The firm's own mark: its company logo when it was picked from the universe, else a tile with its initial. */
export function WorkspaceMark({ workspace, size }: { workspace: WorkspaceSummary; size: number }) {
  if (workspace.company?.logoUrl) {
    return <CompanyLogo name={workspace.name} logo={workspace.company.logoUrl} size={size} />;
  }
  return (
    <span
      style={{ width: size, height: size }}
      className="grid flex-none place-items-center rounded-md bg-u-accent-solid font-mono text-meta font-bold text-white"
    >
      {workspace.logoMark ?? workspace.name[0]}
    </span>
  );
}
