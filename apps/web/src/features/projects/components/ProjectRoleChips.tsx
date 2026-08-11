import { STAFF_ROLES, type StaffRole } from "../api/types";

/**
 * The Roles column of the Team & access table (Project.dc.html): one chip per staff role, the held one
 * lit and check-marked. A seat holds exactly one role, so these are radio buttons wearing the mockup's
 * chip styling — clicking an unlit chip moves the role, clicking the lit one does nothing.
 */

interface RoleStyle {
  label: string;
  /** What the role is for, shown once in the legend above the table. */
  description: string;
  on: string;
}

export const ROLE_STYLES: Record<StaffRole, RoleStyle> = {
  LEAD: {
    label: "Lead",
    description: "owns the mandate, its team and client access",
    on: "text-amber bg-amber-dim border-amber",
  },
  RESEARCHER: {
    label: "Researcher",
    description: "sources and verifies candidates",
    on: "text-text2 bg-panel2 border-line",
  },
};

const CHIP =
  "inline-flex items-center gap-[5px] rounded-full border px-2.5 py-1 font-mono text-[10.5px] " +
  "font-semibold uppercase tracking-[0.04em] transition";

const OFF = "text-text3 bg-transparent border-line";

export function ProjectRoleChips({
  memberName,
  role,
  canManage,
  isSoleLead = false,
  pending,
  onChange,
}: {
  memberName: string;
  role: StaffRole;
  canManage: boolean;
  /** The last lead standing: the server refuses the demotion, so the chip refuses the click first. */
  isSoleLead?: boolean;
  pending: boolean;
  onChange: (role: StaffRole) => void;
}) {
  return (
    <div role="radiogroup" aria-label={`Project role for ${memberName}`} className="flex flex-wrap gap-1.5">
      {STAFF_ROLES.map((candidate) => {
        const held = candidate === role;
        const style = ROLE_STYLES[candidate];
        // Demoting the only lead would 409 with PROJECT_LAST_LEAD. Saying so on the chip beats
        // letting the click through to a toast — and matches the padlock on the same row's remove.
        const wouldStrandTheMandate = isSoleLead && !held;
        const locked = !canManage || pending || wouldStrandTheMandate;
        return (
          <button
            key={candidate}
            type="button"
            role="radio"
            aria-checked={held}
            disabled={locked}
            title={
              !canManage
                ? "Only project leads can change roles"
                : wouldStrandTheMandate
                  ? "A mandate must keep a lead — make someone else lead first"
                  : held
                    ? `${memberName} is already ${style.label.toLowerCase()}`
                    : `Make ${memberName} ${style.label.toLowerCase()}`
            }
            onClick={() => !held && onChange(candidate)}
            className={`${CHIP} ${held ? style.on : OFF} ${
              canManage ? "cursor-pointer" : "cursor-default"
            } ${!held && (!canManage || wouldStrandTheMandate) ? "opacity-50" : ""} disabled:cursor-default`}
          >
            {held && (
              <svg
                width="11"
                height="11"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="3.2"
                aria-hidden="true"
              >
                <path d="m5 13 4 4L19 7" />
              </svg>
            )}
            {style.label}
          </button>
        );
      })}
    </div>
  );
}

/** The legend above the table: the same chips, unclickable, each with what the role is for. */
export function ProjectRoleLegend() {
  return (
    <div className="mb-4 flex flex-wrap items-center gap-x-5 gap-y-2 font-mono text-[11.5px]">
      {STAFF_ROLES.map((candidate) => (
        <span key={candidate} className="flex items-center gap-2">
          <span
            className={`rounded-[5px] border px-[7px] py-0.5 font-mono text-[9px] font-semibold uppercase tracking-[0.05em] ${ROLE_STYLES[candidate].on}`}
          >
            {ROLE_STYLES[candidate].label}
          </span>
          <span className="text-text3">{ROLE_STYLES[candidate].description}</span>
        </span>
      ))}
    </div>
  );
}
