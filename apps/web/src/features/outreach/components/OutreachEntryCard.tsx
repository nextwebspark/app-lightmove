import type { ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { NetworkMark } from "../../../components/ui/NetworkMark";
import { cn } from "../../../lib/cn";
import { ActorFace } from "../../candidates/components/activity/ActivityVisuals";
import { actorNameOf, type ActivityActor, type ActivityExecutive } from "../../candidates/lib/activityPresentation";
import { cardTimeOf, type EntryTile } from "../lib/conversationVocabulary";

/**
 * One entry of an executive's Outreach history, as the drawer and the Outreach page both draw it: the mark of its kind,
 * its title, who did it as a face beside the time, then whatever it carries.
 */
export function OutreachEntryCard({
  label,
  tile,
  title,
  badge = null,
  at = null,
  menu = null,
  actor = null,
  byline = null,
  footer = null,
  id,
  tabIndex,
  children,
}: {
  label: string;
  tile: EntryTile;
  title: ReactNode;
  badge?: ReactNode;
  at?: string | null;
  menu?: ReactNode;
  /** Who did it: an `EntryActor`, drawn beside the time. */
  actor?: ReactNode;
  /** What the line adds beneath the title — where an email went, when a call was. */
  byline?: ReactNode;
  footer?: ReactNode;
  id?: string;
  tabIndex?: number;
  children?: ReactNode;
}) {
  return (
    <article
      aria-label={label}
      id={id}
      tabIndex={tabIndex}
      className="rounded-u-chip border border-u-border px-3 py-2.5 outline-none"
    >
      <div className="flex items-start gap-2.5">
        <EntryLeadMark tile={tile} />
        <div className="min-w-0 flex-1">
          <div className="flex items-baseline gap-2">
            <span className="min-w-0 flex-1 text-body font-semibold text-u-text">
              {title}
              {badge && <span className="ms-2 inline-flex align-middle">{badge}</span>}
            </span>
            {(actor || at) && (
              <span className="flex flex-none items-center gap-1.5 self-center">
                {actor}
                {at && (
                  <time dateTime={at} className="font-mono text-meta text-u-text3">
                    {cardTimeOf(at)}
                  </time>
                )}
              </span>
            )}
            {menu && <span className="flex-none self-center">{menu}</span>}
          </div>
          {byline && <div className="mt-1 flex min-w-0 items-center gap-1.5 text-note text-u-text2">{byline}</div>}
        </div>
      </div>
      {children}
      {footer && <div className="mt-2 font-mono text-meta text-u-text3">{footer}</div>}
    </article>
  );
}

/** Who did it, as a face alone: the name is its tooltip and what a screen reader hears. */
export function EntryActor({
  actor,
  executive,
  name,
}: {
  actor: ActivityActor;
  executive: ActivityExecutive;
  /** In place of the actor's own name: Uncava, on a delivery it reports. */
  name?: string;
}) {
  const shownName = name ?? actorNameOf(actor, executive);
  return (
    <span className="inline-flex flex-none">
      <ActorFace actor={actor} executive={executive} size="xs" title={shownName} />
      <span className="sr-only">{shownName}</span>
    </span>
  );
}

/** A kind's tile, tinted as its tone reads elsewhere — LinkedIn's and WhatsApp's being the network's own mark, never tinted. */
export function EntryLeadMark({ tile }: { tile: EntryTile }) {
  if (tile === "linkedin" || tile === "whatsapp") {
    return (
      <span
        aria-hidden="true"
        className="grid size-8 flex-none place-items-center rounded-u-chip border border-u-border bg-u-bg"
      >
        <NetworkMark network={tile} size={15} />
      </span>
    );
  }
  const { icon, tone } = TILES[tile];
  return (
    <span aria-hidden="true" className={cn("grid size-8 flex-none place-items-center rounded-u-chip", tone)}>
      <Icon d={icon} size={16} />
    </span>
  );
}

const TILES: Record<Exclude<EntryTile, "linkedin" | "whatsapp">, { icon: string; tone: string }> = {
  email: { icon: ICONS.mail, tone: "bg-u-accent-tint text-u-accent" },
  scheduled: { icon: ICONS.clock, tone: "bg-u-signal-tint text-u-signal" },
  reply: { icon: ICONS.reply, tone: "bg-u-direct-tint text-u-direct" },
  problem: { icon: ICONS.warning, tone: "bg-u-offlimits-tint text-u-offlimits" },
  call: { icon: ICONS.phoneCall, tone: "bg-u-raised text-u-text2" },
  note: { icon: ICONS.fileText, tone: "bg-u-raised text-u-text2" },
  video: { icon: ICONS.video, tone: "bg-u-direct-tint text-u-direct" },
  meeting: { icon: ICONS.calendar, tone: "bg-u-direct-tint text-u-direct" },
};

/** A call the Notetaker recorded: set apart from a calendar entry nobody recorded. */
export function RecordedBadge() {
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-u-raised px-1.5 py-px text-meta font-medium text-u-text2">
      <Icon d={ICONS.mic} size={11} className="text-u-text3" />
      Recorded
    </span>
  );
}
