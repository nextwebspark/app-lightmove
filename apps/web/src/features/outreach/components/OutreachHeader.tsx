import type { ReactNode } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { SegmentedControl } from "../../../components/ui";
import { TOOLBAR_PRIMARY, TOOLBAR_ROW, ToolbarDivider } from "../../../components/ui/Toolbar";

export type OutreachSection = "conversations" | "sequences";

const SECTIONS = [
  { value: "conversations", label: "Conversations" },
  { value: "sequences", label: "Sequences" },
] as const;

/**
 * The header both Outreach views share, the sequence editor under Sequences included: the view's search where it has
 * one, then the mailbox chip and the view's own action. From `lg` the rail holds Conversations and Sequences; below it,
 * where there is no rail, the header's switch does, and Sequences from the editor is the way back to the list.
 */
export function OutreachHeader({
  projectId,
  section,
  search,
  chip,
  action,
}: {
  projectId: string;
  section: OutreachSection;
  search?: ReactNode;
  /** The mailbox chip, only where it draws something. */
  chip?: ReactNode;
  action?: ReactNode;
}) {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const base = `/projects/${projectId}/outreach`;
  return (
    <div className={TOOLBAR_ROW}>
      <SegmentedControl
        label="Outreach view"
        options={SECTIONS}
        value={section}
        className="lg:hidden"
        onChange={(next) => {
          const to = next === "sequences" ? `${base}/sequences` : base;
          if (pathname.replace(/\/$/, "") !== to) navigate(to);
        }}
      />
      {search}
      <div className="ms-auto flex min-w-0 items-center gap-3">
        {chip}
        {chip && action && <ToolbarDivider />}
        {action}
      </div>
    </div>
  );
}

/** The Sequences view's action. */
export function NewSequenceLink({ projectId }: { projectId: string }) {
  return (
    <Link to={`/projects/${projectId}/outreach/sequences/new`} className={TOOLBAR_PRIMARY}>
      <Icon d={ICONS.plus} size={14} className="flex-none" />
      New sequence
    </Link>
  );
}
