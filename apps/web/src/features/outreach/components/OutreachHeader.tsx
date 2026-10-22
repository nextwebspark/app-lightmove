import type { ReactNode } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { SegmentedControl } from "../../../components/ui";

export type OutreachSection = "conversations" | "sequences";

const SECTIONS = [
  { value: "conversations", label: "Conversations" },
  { value: "sequences", label: "Sequences" },
] as const;

/** The header's one filled button, whichever view's own action it is. */
export const OUTREACH_PRIMARY_ACTION =
  "inline-flex flex-none items-center gap-1.5 whitespace-nowrap rounded-u-control border border-u-accent-solid bg-u-accent-solid px-3 py-1.5 text-note font-medium text-white hover:bg-u-accent-solid-hover";

/**
 * The header both Outreach views share, the sequence editor under Sequences included: Conversations or Sequences, and
 * whatever the view puts beside it — the mailbox chip and its own action. Sequences from the editor is the way back
 * to the list.
 */
export function OutreachHeader({
  projectId,
  section,
  children,
}: {
  projectId: string;
  section: OutreachSection;
  children?: ReactNode;
}) {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const base = `/projects/${projectId}/outreach`;
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-2 border-b border-u-border px-4 py-2 md:px-6">
      <SegmentedControl
        label="Outreach view"
        options={SECTIONS}
        value={section}
        onChange={(next) => {
          const to = next === "sequences" ? `${base}/sequences` : base;
          if (pathname.replace(/\/$/, "") !== to) navigate(to);
        }}
      />
      <div className="ms-auto flex min-w-0 items-center gap-2">{children}</div>
    </div>
  );
}

/** The Sequences view's action. */
export function NewSequenceLink({ projectId }: { projectId: string }) {
  return (
    <Link to={`/projects/${projectId}/outreach/sequences/new`} className={OUTREACH_PRIMARY_ACTION}>
      <Icon d={ICONS.plus} size={13} />
      New sequence
    </Link>
  );
}
