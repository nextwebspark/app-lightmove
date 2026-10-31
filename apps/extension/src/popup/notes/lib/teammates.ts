// Ported from apps/web/src/features/tasks/lib/team.ts; keep the two in step.
import type { TeamMember } from "../../../api/types";

/** Someone a note can mention: a staff seat on the position. */
export interface TeammateOption {
  userId: string;
  fullName: string;
  avatarUrl: string | null;
  isMe: boolean;
  role: "Lead" | "Researcher" | null;
}

const byName = (a: TeammateOption, b: TeammateOption) => a.fullName.localeCompare(b.fullName);

/** The position's team as the picker lists it: me first, then the leads, then the researchers, each A–Z. Never a client seat. */
export function teammatesOf(team: readonly TeamMember[], viewerId: string | null): TeammateOption[] {
  const staff = team
    .filter((member) => member.projectRoles.some((role) => role === "LEAD" || role === "RESEARCHER"))
    .map<TeammateOption>((member) => ({
      userId: member.userId,
      fullName: member.fullName,
      avatarUrl: member.avatarUrl,
      isMe: member.userId === viewerId,
      role: member.projectRoles.includes("LEAD") ? "Lead" : "Researcher",
    }));
  const me = staff.filter((option) => option.isMe);
  const others = staff.filter((option) => !option.isMe);
  return [
    ...me,
    ...others.filter((option) => option.role === "Lead").sort(byName),
    ...others.filter((option) => option.role === "Researcher").sort(byName),
  ];
}
