import type { TeamMember } from "../../projects/api/types";
import type { TaskPerson } from "../api/types";

/** Someone a task can be assigned to, or a note can mention: a staff seat on the position. */
export interface TeammateOption {
  userId: string;
  fullName: string;
  avatarUrl: string | null;
  isMe: boolean;
  role: "Lead" | "Researcher" | null;
}

export interface Viewer {
  id: string;
  fullName: string;
  avatarUrl: string | null;
}

const byName = (a: TeammateOption, b: TeammateOption) => a.fullName.localeCompare(b.fullName);

/**
 * The position's team as every picker lists it: me first, then the leads, then the researchers, each A–Z.
 * Only staff seats — a client seat is never offered. I am offered even without a seat, since only someone who
 * may work the position opens a picker at all. A current assignee the team no longer lists is kept, last, so
 * an edit does not silently reassign them.
 */
export function teammatesOf(
  team: readonly TeamMember[],
  viewer: Viewer | null,
  keep?: TaskPerson | null,
): TeammateOption[] {
  const staff = team
    .filter((member) => member.projectRoles.some((role) => role === "LEAD" || role === "RESEARCHER"))
    .map<TeammateOption>((member) => ({
      userId: member.userId,
      fullName: member.fullName,
      avatarUrl: member.avatarUrl,
      isMe: member.userId === viewer?.id,
      role: member.projectRoles.includes("LEAD") ? "Lead" : "Researcher",
    }));
  const mine = staff.find((option) => option.isMe);
  const me: TeammateOption[] = viewer
    ? [mine ?? { userId: viewer.id, fullName: viewer.fullName, avatarUrl: viewer.avatarUrl, isMe: true, role: null }]
    : [];
  const others = staff.filter((option) => !option.isMe);
  const listed = [
    ...me,
    ...others.filter((option) => option.role === "Lead").sort(byName),
    ...others.filter((option) => option.role === "Researcher").sort(byName),
  ];
  if (keep && !listed.some((option) => option.userId === keep.userId)) {
    listed.push({
      userId: keep.userId,
      fullName: keep.fullName ?? "Someone",
      avatarUrl: keep.avatarUrl,
      isMe: false,
      role: null,
    });
  }
  return listed;
}

/** "Me · Yara Haddad" for the viewer, the name alone for anyone else. */
export function teammateLabel(option: TeammateOption): string {
  return option.isMe ? `Me · ${option.fullName}` : option.fullName;
}

export function firstNameOf(fullName: string): string {
  return fullName.trim().split(/\s+/)[0] ?? fullName;
}
