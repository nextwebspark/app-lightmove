import type { ExtractedCareerEntry } from "../../content/pageReader/extractedPerson";

interface PreviousRolesListProps {
  roles: ExtractedCareerEntry[];
}

/**
 * The career the page listed, read-only.
 *
 * Not editable, unlike every other field here: a role is three fields, and editing a list of them in a
 * 400px popup is the drawer's job. What the page said is written through as-is or not at all.
 */
export function PreviousRolesList({ roles }: PreviousRolesListProps) {
  if (roles.length === 0) {
    return null;
  }
  return (
    <ul className="divide-y divide-line-soft rounded-lg border border-line">
      {roles.map((role, index) => (
        <li key={`${role.company}-${role.title}-${index}`} className="flex items-baseline gap-2 px-2.5 py-[7px]">
          <span className="flex-1">
            <span className="block text-[12px] font-medium text-text">{role.title ?? "—"}</span>
            <span className="block text-[10.5px] text-text3">{role.company ?? "—"}</span>
          </span>
          <span className="shrink-0 font-mono text-[10.5px] text-text3">{role.period ?? ""}</span>
        </li>
      ))}
    </ul>
  );
}
