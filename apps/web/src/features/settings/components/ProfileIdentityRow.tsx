import { Avatar } from "../../../components/ui";
import { formatMonthYear, titleCase } from "../../../lib/format";
import type { WorkspaceRole } from "../../auth/api/types";

/**
 * Who the caller is, as the workspace sees them: the identity tile, their name, and the standing that
 * is not theirs to edit.
 *
 * Presentational and props-only — the mockup's "Change photo" button is absent because nothing can
 * store a picture yet; a provider's is shown when there is one, initials otherwise.
 */
export function ProfileIdentityRow({
  userId,
  fullName,
  avatarUrl,
  roles,
  joinedAt,
}: {
  userId: string;
  fullName: string;
  avatarUrl: string | null;
  roles: WorkspaceRole[];
  joinedAt: string | null;
}) {
  return (
    <div className="mb-5 flex items-center gap-3.5">
      <Avatar id={userId} name={fullName} src={avatarUrl} size="xl" className="rounded-xl" />
      <div>
        <div className="text-sm font-semibold">{fullName}</div>
        <div className="mt-0.5 font-mono text-[11.5px] text-text3">{membershipMeta(roles, joinedAt)}</div>
      </div>
    </div>
  );
}

/**
 * "Admin · joined Mar 2026" — and the roles alone when the join date is missing, rather than a dash
 * mid-sentence. A member may hold more than one role, so this reads them all.
 */
function membershipMeta(roles: WorkspaceRole[], joinedAt: string | null): string {
  const standing = roles.map(titleCase).join(", ");
  const joined = formatMonthYear(joinedAt);
  return joined ? `${standing} · joined ${joined}` : standing;
}
