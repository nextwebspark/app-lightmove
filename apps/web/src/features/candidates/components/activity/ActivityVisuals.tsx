import { Icon } from "../../../../components/layout/Icon";
import { Avatar } from "../../../../components/ui/Avatar";
import { AppIcon } from "../../../../components/ui";
import { cn } from "../../../../lib/cn";
import { TONE_CLASSES, type ActivityActor, type ActivityExecutive, type ActivityTone } from "../../lib/activityPresentation";
import { CandidateAvatar } from "../CandidateAvatar";


/** A kind's glyph on its tint: the spine's tile on a card, a dot on a slim row, the head of a dialog. */
export function KindTile({
  tone,
  icon,
  size,
  className,
}: {
  tone: ActivityTone;
  icon: string;
  size: "dot" | "tile" | "head";
  className?: string;
}) {
  const box = size === "dot" ? "size-[22px] rounded-full" : size === "tile" ? "size-[30px] rounded-lg" : "size-9 rounded-xl";
  return (
    <span className={cn("grid flex-none place-items-center", box, TONE_CLASSES[tone], className)} aria-hidden="true">
      <Icon d={icon} size={size === "dot" ? 12 : size === "tile" ? 15 : 18} />
    </span>
  );
}


/** Whoever did it, as a face: a colleague's picture or initials, the executive's photo, or Uncava's own mark. */
export function ActorFace({
  actor,
  executive,
  projectId,
  size = "sm",
  title,
}: {
  actor: ActivityActor;
  executive: ActivityExecutive;
  projectId: string;
  size?: "xs" | "sm" | "md" | "lg";
  title?: string;
}) {
  if (actor.kind === "user") {
    return <Avatar id={actor.id} name={actor.name} src={actor.avatarUrl} size={size} title={title} />;
  }
  if (actor.kind === "executive") {
    return (
      <span title={title} className="inline-flex flex-none">
        <CandidateAvatar
          projectId={projectId}
          candidate={executive}
          size={size === "xs" ? "sm" : size}
          className={size === "xs" ? "size-[18px]" : undefined}
        />
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
