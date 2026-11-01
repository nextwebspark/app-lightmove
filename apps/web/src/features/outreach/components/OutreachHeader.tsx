import type { ReactNode } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import {
  TOOLBAR_PRIMARY,
  TOOLBAR_ROW,
  ToolbarDivider,
  ToolbarViewSwitch,
  type ToolbarView,
} from "../../../components/ui/Toolbar";
import { cn } from "../../../lib/cn";

export type OutreachSection = "conversations" | "sequences";

const SECTIONS: readonly ToolbarView<OutreachSection>[] = [
  { value: "conversations", label: "Conversations", icon: ICONS.mail },
  { value: "sequences", label: "Sequences", icon: ICONS.listOrdered },
];

/**
 * The header both Outreach views share, the sequence editor under Sequences included: Conversations | Sequences, the
 * view's search where it has one, then the mailbox chip and the view's own action. Sequences from the editor is the
 * way back to the list.
 */
export function OutreachHeader({
  projectId,
  section,
  search,
  searchFromLg = false,
  chip,
  action,
}: {
  projectId: string;
  section: OutreachSection;
  search?: ReactNode;
  /** The search only from `lg`, as while a conversation fills a narrower screen and there is no list to search. */
  searchFromLg?: boolean;
  /** The mailbox chip, only where it draws something. */
  chip?: ReactNode;
  action?: ReactNode;
}) {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const base = `/projects/${projectId}/outreach`;
  const divider = !search ? "none" : searchFromLg ? "lg" : "sm";
  return (
    <div className={TOOLBAR_ROW}>
      <ToolbarViewSwitch
        label="Outreach view"
        views={SECTIONS}
        value={section}
        divider={divider}
        onChange={(next) => {
          const to = next === "sequences" ? `${base}/sequences` : base;
          if (pathname.replace(/\/$/, "") !== to) navigate(to);
        }}
      />
      {search && <div className={cn("contents", searchFromLg && "max-lg:hidden")}>{search}</div>}
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
