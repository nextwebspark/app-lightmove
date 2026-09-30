import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Avatar } from "../../../../components/ui/Avatar";
import { SelectionCheckbox } from "../../../../components/ui/SelectionCheckbox";
import type { PersonResult } from "../../api/types";

/**
 * The people a search returned, in ContactOut's order — its API states no sort and no relevance, so
 * neither does this table. A person the mandate already maps is marked and cannot be ticked: they
 * came back, and were billed, because ContactOut cannot leave a person out.
 */
export function PersonResultsTable({
  people,
  selected,
  onToggle,
  onToggleAll,
}: {
  people: PersonResult[];
  selected: Set<string>;
  onToggle: (linkedinSlug: string) => void;
  onToggleAll: (linkedinSlugs: string[]) => void;
}) {
  const tickable = people.filter((person) => !person.held).map((person) => person.linkedinSlug);
  const tickedCount = tickable.filter((slug) => selected.has(slug)).length;

  return (
    <div className="min-h-0 flex-1 overflow-auto rounded-[8px] border border-u-border bg-u-surface">
      <table className="w-full min-w-[720px] border-collapse text-left">
        <thead className="sticky top-0 z-[1] bg-u-raised">
          <tr className="border-b border-u-border text-meta font-semibold uppercase tracking-[0.06em] text-u-text3">
            <th className="w-10 px-3 py-2.5">
              <SelectionCheckbox
                checked={tickable.length > 0 && tickedCount === tickable.length}
                indeterminate={tickedCount > 0 && tickedCount < tickable.length}
                label="Select everyone on this list"
                onChange={() => onToggleAll(tickable)}
              />
            </th>
            <th className="px-3 py-2.5">Person</th>
            <th className="px-3 py-2.5">Company</th>
            <th className="px-3 py-2.5">Location</th>
            <th className="w-32 px-3 py-2.5" />
          </tr>
        </thead>
        <tbody>
          {people.map((person) => (
            <tr key={person.linkedinSlug} className="border-b border-u-border last:border-b-0 hover:bg-u-raised">
              <td className="px-3 py-2.5 align-top">
                {!person.held && (
                  <SelectionCheckbox
                    checked={selected.has(person.linkedinSlug)}
                    label={`Select ${person.fullName ?? person.linkedinSlug}`}
                    onChange={() => onToggle(person.linkedinSlug)}
                  />
                )}
              </td>
              <td className="px-3 py-2.5 align-top">
                <div className="flex items-start gap-2.5">
                  <Avatar id={person.linkedinSlug} name={person.fullName ?? person.linkedinSlug} src={person.photoUrl} size="sm" />
                  <div className="min-w-0">
                    <a
                      href={person.profileUrl ?? undefined}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex items-center gap-1 text-note font-semibold text-u-text hover:text-u-accent"
                    >
                      {person.fullName ?? person.linkedinSlug}
                      <Icon d={ICONS.externalLink} size={11} className="flex-none text-u-text3" />
                    </a>
                    <p className="text-meta text-u-text2">{person.title ?? "—"}</p>
                  </div>
                </div>
              </td>
              <td className="px-3 py-2.5 align-top text-note text-u-text2">
                {person.companyLinkedinUrl ? (
                  <a href={person.companyLinkedinUrl} target="_blank" rel="noreferrer" className="hover:text-u-accent">
                    {person.companyName ?? "—"}
                  </a>
                ) : (
                  (person.companyName ?? "—")
                )}
              </td>
              <td className="px-3 py-2.5 align-top text-note text-u-text2">{person.location ?? "—"}</td>
              <td className="px-3 py-2.5 align-top text-right">
                {person.held && (
                  <span className="rounded-[4px] bg-u-accent-tint px-1.5 py-[2px] text-meta font-semibold text-u-accent">
                    In universe
                  </span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
