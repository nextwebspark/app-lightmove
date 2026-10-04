import { cn } from "../../lib/cn";
import { Avatar } from "./Avatar";

export interface StackedPerson {
  id: string;
  name: string;
  src?: string | null;
  /** The tooltip; the name alone when omitted. */
  title?: string;
}

/**
 * Overlapping avatars, the first `max` drawn and the rest folded into a `+N` chip that names them.
 * Ringed in the page ground, so it belongs on `u-bg` — the header, not a card.
 */
export function AvatarStack({ people, max, className }: { people: StackedPerson[]; max: number; className?: string }) {
  const shown = people.slice(0, max);
  const hidden = people.slice(max);
  return (
    <span className={cn("flex items-center", className)}>
      {shown.map((person, index) => (
        // The disc underneath is opaque: initials are drawn on a translucent tint, and without it the
        // avatar they overlap shows through and reads as one torn circle.
        <span key={person.id} className={cn("flex rounded-full bg-u-bg ring-2 ring-u-bg", index > 0 && "-ml-[5px]")}>
          <Avatar id={person.id} name={person.name} src={person.src} title={person.title} size="sm" />
        </span>
      ))}
      {hidden.length > 0 && (
        <span
          title={hidden.map((person) => person.name).join(", ")}
          className="-ml-[5px] grid size-6 shrink-0 place-items-center rounded-full bg-u-raised font-mono text-[10px] font-semibold text-u-text2 ring-2 ring-u-bg"
        >
          +{hidden.length}
        </span>
      )}
    </span>
  );
}
