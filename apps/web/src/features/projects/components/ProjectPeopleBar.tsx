import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { AvatarStack, type StackedPerson } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { useAuth } from "../../auth/AuthProvider";
import type { Project, TeamMember } from "../api/types";
import { canManageProjectAccess } from "../lib/access";

const TEAM_SHOWN = 4;
const TEAM_SHOWN_ON_TABLET = 3;
/** A phone draws one stack of everyone, two faces deep, or it pushes the header off the screen. */
const EVERYONE_SHOWN_ON_PHONE = 2;
const CLIENTS_SHOWN = 3;

const GROUP =
  "flex items-center gap-2 rounded-[7px] px-1.5 py-1 transition hover:bg-u-raised focus-visible:outline-2 focus-visible:outline-u-accent";
// The labels and the invited pill go first as the header narrows, before the breadcrumb has to give way.
const GROUP_LABEL = "type-tag hidden text-u-text3 lg:inline";

/**
 * Everyone on the mandate, in the project header: its staff, its hiring managers, and — for whoever
 * may seat people — the way to invite more. Both groups and the button open Team & access, the one
 * place a seat is actually changed.
 */
export function ProjectPeopleBar({ project }: { project: Project }) {
  const { user } = useAuth();
  const teamHref = `/projects/${project.id}/team`;
  const canInvite = canManageProjectAccess(project, user?.id, user?.workspace?.roles);

  const staff = project.team
    .filter((member) => member.projectRoles.some((role) => role !== "CLIENT"))
    .sort((a, b) => Number(isLead(b)) - Number(isLead(a)));
  const staffPeople: StackedPerson[] = staff.map((member) => ({
    id: member.memberId,
    name: member.fullName,
    src: member.avatarUrl,
    title: `${member.fullName} · ${isLead(member) ? "Lead" : "Researcher"}`,
  }));

  const active = project.representatives.filter((rep) => rep.status === "ACTIVE");
  const invited = project.representatives.length - active.length;
  const clientPeople: StackedPerson[] = active.map((rep) => ({
    id: rep.representativeId,
    name: rep.fullName,
    src: rep.avatarUrl,
    title: `${rep.fullName} · Hiring manager`,
  }));

  return (
    <div className="flex min-w-0 items-center gap-1.5 sm:gap-2.5">
      <Link to={teamHref} aria-label={`Team: ${peopleCount(staff.length)}. Open Team & access`} className={GROUP}>
        <span className={GROUP_LABEL}>Team</span>
        {staffPeople.length > 0 ? (
          <>
            <AvatarStack people={[...staffPeople, ...clientPeople]} max={EVERYONE_SHOWN_ON_PHONE} className="sm:hidden" />
            <AvatarStack people={staffPeople} max={TEAM_SHOWN_ON_TABLET} className="hidden sm:flex lg:hidden" />
            <AvatarStack people={staffPeople} max={TEAM_SHOWN} className="hidden lg:flex" />
          </>
        ) : (
          <span className="font-mono text-meta text-u-text3">None yet</span>
        )}
      </Link>

      <span aria-hidden="true" className="hidden h-5 w-px bg-u-border sm:block" />

      <Link
        to={teamHref}
        aria-label={`Hiring managers: ${peopleCount(active.length)}${invited > 0 ? `, ${invited} invited` : ""}. Open Team & access`}
        className={cn(GROUP, "hidden sm:flex")}
      >
        <span className={GROUP_LABEL}>Clients</span>
        {invited > 0 && (
          <span
            className={cn(
              "whitespace-nowrap rounded-full bg-u-signal-tint px-2 py-0.5 text-meta font-semibold text-u-signal",
              // Kept at every width when nobody has accepted yet: it is then all the group has to say.
              clientPeople.length > 0 && "hidden lg:inline",
            )}
          >
            {invited} invited
          </span>
        )}
        {clientPeople.length > 0 && <AvatarStack people={clientPeople} max={CLIENTS_SHOWN} />}
        {clientPeople.length === 0 && invited === 0 && (
          <span className="whitespace-nowrap font-mono text-meta text-u-text3">No client rep</span>
        )}
      </Link>

      {canInvite && (
        <Link
          to={teamHref}
          aria-label="Invite team members or hiring managers"
          className="flex items-center gap-1.5 rounded-[6px] border border-u-border-strong bg-u-surface px-2 py-1.5 text-note font-medium text-u-text2 transition hover:border-u-text3 hover:text-u-text sm:px-3"
        >
          <Icon d={ICONS.plus} size={14} />
          <span className="hidden sm:inline">Invite</span>
        </Link>
      )}
    </div>
  );
}

function isLead(member: TeamMember): boolean {
  return member.projectRoles.includes("LEAD");
}

function peopleCount(count: number): string {
  return count === 1 ? "1 person" : `${count} people`;
}
