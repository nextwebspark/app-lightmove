import { Icon } from "../../../../components/layout/Icon";
import { Avatar } from "../../../../components/ui/Avatar";
import { AppIcon } from "../../../../components/ui";
import { cn } from "../../../../lib/cn";
import type { ActivityActor, ActivityExecutive } from "../../lib/activityPresentation";


/** A kind's glyph, grey and plain as the app's menus draw theirs: on the spine, in a fold, in a menu, at a dialog's head. */
export function KindTile({ icon, size, className }: { icon: string; size: "dot" | "tile" | "head"; className?: string }) {
  const box = size === "dot" ? "size-[22px]" : size === "tile" ? "size-[30px]" : "size-9";
  return (
    <span className={cn("grid flex-none place-items-center text-u-text3", box, className)} aria-hidden="true">
      <Icon d={icon} size={size === "dot" ? 14 : size === "tile" ? 16 : 20} />
    </span>
  );
}

/** Whoever did it, as a face: a colleague's picture or initials, the executive's photo, or Uncava's own mark. */
export function ActorFace({
  actor,
  executive,
  size = "sm",
  title,
}: {
  actor: ActivityActor;
  executive: ActivityExecutive;
  size?: "xs" | "sm" | "md" | "lg";
  title?: string;
}) {
  if (actor.kind === "user") {
    return <Avatar id={actor.id} name={actor.name} src={actor.avatarUrl} size={size} title={title} />;
  }
  if (actor.kind === "executive") {
    return (
      <span title={title} className="inline-flex flex-none">
        {executive.face(size === "xs" ? "sm" : size, size === "xs" ? "size-[18px]" : undefined)}
      </span>
    );
  }
  const box = size === "xs" ? "size-[18px]" : size === "sm" ? "size-6" : size === "md" ? "size-[26px]" : "size-[30px]";
  if (actor.kind === "automation") {
    return (
      <span
        title={title ?? "Automation"}
        className={cn("inline-grid flex-none place-items-center rounded-full bg-u-accent-tint", box)}
      >
        <AppIcon className="h-[62%]" />
      </span>
    );
  }
  return (
    <span
      title={title ?? "Someone"}
      className={cn("inline-grid flex-none place-items-center rounded-full border border-dashed border-u-border-strong bg-u-sunken text-eyebrow font-semibold text-u-text3", box)}
    >
      ?
    </span>
  );
}
