import type { ReactNode } from "react";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { cn } from "../../../../lib/cn";
import type { FieldChange, PersonTimelineEntry } from "../../api/types";
import {
  actorOf,
  asideOf,
  cardTimeOf,
  fieldLabel,
  fullDateTime,
  iconOf,
  isOrigin,
  previewOf,
  shortDateTime,
  toneOf,
  verbOf,
  type ActivityPreview,
  actorNameOf,
  type ActivityExecutive,
} from "../../lib/activityPresentation";
import { ActorFace, KindTile } from "./ActivityVisuals";

interface ItemProps {
  entry: PersonTimelineEntry;
  executive: ActivityExecutive;
  projectId: string;
  now: Date;
  onOpen: (entry: PersonTimelineEntry) => void;
}

/** A content event: a card on the spine, its kind's tile beside it, who and when on top and a preview beneath. */
export function ActivityCard({ entry, executive, projectId, now, onOpen }: ItemProps) {
  const actor = actorOf(entry);
  const name = actorNameOf(actor, executive);
  const preview = previewOf(entry);
  return (
    <li className="relative">
      <KindTile tone={toneOf(entry)} icon={iconOf(entry)} size="tile" className="absolute -start-[42px] top-2.5 ring-3 ring-u-surface" />
      <button
        type="button"
        onClick={() => onOpen(entry)}
        aria-label={`${name} ${verbOf(entry)}, ${fullDateTime(entry.occurredAt)}. Open details`}
        className={cn(
          "group relative flex w-full flex-col gap-2 rounded-xl border bg-u-bg px-3 pb-3 pt-2.5 text-start transition",
          "hover:-translate-y-px hover:border-u-border-strong hover:shadow-u-e2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-u-accent-ring",
          isOrigin(entry) ? "border-dashed border-u-border-strong" : "border-u-border",
        )}
      >
        <WhoAndWhen entry={entry} executive={executive} projectId={projectId} now={now} />
        {preview && <PreviewOf preview={preview} />}
        <span className="pointer-events-none absolute bottom-2.5 end-2.5 text-u-text3 opacity-0 transition group-hover:opacity-100 group-focus-visible:opacity-100">
          <Icon d={ICONS.chevronRight} size={14} />
        </span>
      </button>
    </li>
  );
}

/** A minor change: one slim line on the spine, still a face, a name and a time. */
export function ActivityRow({ entry, executive, projectId, now, onOpen, nested = false }: ItemProps & { nested?: boolean }) {
  const actor = actorOf(entry);
  const name = actorNameOf(actor, executive);
  const aside = asideOf(entry);
  const time = cardTimeOf(entry.occurredAt, now);
  const row = (
    <button
      type="button"
      onClick={() => onOpen(entry)}
      aria-label={`${name} ${verbOf(entry)}${aside ? `, ${aside}` : ""}, ${fullDateTime(entry.occurredAt)}. Open details`}
      className={cn(
        "flex w-full min-w-0 items-center gap-2 rounded-lg py-1.5 text-start transition hover:bg-u-raised focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-u-accent-ring",
        nested ? "px-2" : "-ms-2 px-2",
      )}
    >
      {nested && <KindTile tone={toneOf(entry)} icon={iconOf(entry)} size="dot" className="size-[18px]" />}
      <ActorFace actor={actor} executive={executive} projectId={projectId} size="xs" />
      <span className="min-w-0 flex-1 truncate text-body text-u-text2">
        <span className="font-semibold text-u-text">{name}</span> {verbOf(entry)}
        {aside && <span className="text-u-text3"> · {aside}</span>}
      </span>
      <time
        dateTime={entry.occurredAt}
        title={fullDateTime(entry.occurredAt)}
        className={cn("flex-none font-mono text-meta", time.isRecent ? "text-u-accent" : "text-u-text3")}
      >
        {time.text}
      </time>
    </button>
  );
  if (nested) return <li>{row}</li>;
  return (
    <li className="relative">
      <KindTile tone={toneOf(entry)} icon={iconOf(entry)} size="dot" className="absolute -start-[38px] top-1 ring-3 ring-u-surface" />
      {row}
    </li>
  );
}

/** Three or more quick changes by one person, folded to one line that opens in place. */
export function ActivityFold({
  entries,
  isOpen,
  onToggle,
  ...rest
}: Omit<ItemProps, "entry"> & { entries: PersonTimelineEntry[]; isOpen: boolean; onToggle: () => void }) {
  const first = entries[0];
  const last = entries[entries.length - 1];
  const actor = actorOf(first);
  const name = actorNameOf(actor, rest.executive);
  const span = `${clockOf(last.occurredAt)}–${clockOf(first.occurredAt)}`;
  return (
    <li className="relative">
      <KindTile tone="neutral" icon={ICONS.position} size="dot" className="absolute -start-[38px] top-1 ring-3 ring-u-surface" />
      <button
        type="button"
        aria-expanded={isOpen}
        onClick={onToggle}
        className="-ms-2 flex w-full min-w-0 items-center gap-2 rounded-lg px-2 py-1.5 text-start transition hover:bg-u-raised focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-u-accent-ring"
      >
        <ActorFace actor={actor} executive={rest.executive} projectId={rest.projectId} size="xs" />
        <span className="min-w-0 flex-1 truncate text-body text-u-text2">
          <span className="font-semibold text-u-text">{name}</span> made {entries.length} changes
        </span>
        <span className="flex flex-none" aria-hidden="true">
          {entries.slice(0, 4).map((entry, index) => (
            <KindTile
              key={entry.id}
              tone={toneOf(entry)}
              icon={iconOf(entry)}
              size="dot"
              className={cn("size-[18px] ring-2 ring-u-surface", index > 0 && "-ms-1")}
            />
          ))}
        </span>
        <time dateTime={first.occurredAt} title={fullDateTime(first.occurredAt)} className="flex-none font-mono text-meta text-u-text3">
          {span}
        </time>
        <span className={cn("flex-none text-u-text3 transition-transform", isOpen && "rotate-180")}>
          <Icon d={ICONS.chevronDown} size={14} />
        </span>
      </button>
      {isOpen && (
        <ul className="my-0.5 flex flex-col border-s border-dashed border-u-border-strong ps-3" aria-label={`${name}'s ${entries.length} changes`}>
          {entries.map((entry) => (
            <ActivityRow key={entry.id} entry={entry} nested {...rest} />
          ))}
        </ul>
      )}
    </li>
  );
}

function WhoAndWhen({ entry, executive, projectId, now }: Omit<ItemProps, "onOpen">) {
  const actor = actorOf(entry);
  const name = actorNameOf(actor, executive);
  const time = cardTimeOf(entry.occurredAt, now);
  return (
    <span className="flex min-w-0 items-center gap-2">
      <ActorFace actor={actor} executive={executive} projectId={projectId} size="xs" title={name} />
      <span className="min-w-0 flex-1 text-body text-u-text2">
        <span className="font-semibold text-u-text">{name}</span> {verbOf(entry)}
      </span>
      <time
        dateTime={entry.occurredAt}
        title={fullDateTime(entry.occurredAt)}
        className={cn("flex-none font-mono text-meta", time.isRecent ? "text-u-accent" : "text-u-text3")}
      >
        {time.text}
      </time>
    </span>
  );
}

export function PreviewOf({ preview }: { preview: ActivityPreview }) {
  switch (preview.type) {
    case "note":
      return preview.excerpt ? (
        <span className="flex flex-col gap-1.5">
          <span className="line-clamp-2 text-body text-u-text">{preview.excerpt}</span>
          {preview.aboutThePerson && <Chip>About the person · every position</Chip>}
        </span>
      ) : (
        <span className="text-note text-u-text3">This note was deleted.</span>
      );
    case "status":
      return (
        <span className="flex flex-wrap items-center gap-1.5 text-note text-u-text3">
          {preview.from && <StatusPill label={preview.from} />}
          {preview.from && <Icon d={ICONS.arrowRight} size={13} className="text-u-text3" />}
          <StatusPill label={preview.to} isCurrent />
          {preview.cause && <span>{preview.cause}</span>}
        </span>
      );
    case "changes":
      return (
        <span className="flex flex-col gap-1">
          {preview.changes.map((change) => (
            <ChangeLine key={change.field} change={change} />
          ))}
          {preview.hidden > 0 && <span className="text-note text-u-text3">+{preview.hidden} more</span>}
        </span>
      );
    case "fields":
      return <span className="text-note text-u-text3">{preview.labels.join(", ")}</span>;
    case "meeting":
      return <MeetingPreview preview={preview} />;
    case "email":
      return (
        <span className="flex min-w-0 items-center gap-2 rounded-lg border border-u-border bg-u-surface px-2.5 py-1.5 text-note text-u-text2">
          <Icon d={ICONS.mail} size={13} className="flex-none text-u-adjacent" />
          <span className="truncate">{preview.label}</span>
          {preview.detail && <span className="flex-none text-u-text3">· {preview.detail}</span>}
        </span>
      );
    case "document":
      return (
        <span className="flex min-w-0 items-center gap-2.5 rounded-lg border border-u-border bg-u-surface px-2.5 py-1.5">
          <Icon d={ICONS.file} size={16} className="flex-none text-u-offlimits" />
          <span className="min-w-0">
            <span className="block truncate text-body font-medium text-u-text">
              {preview.title ?? `A ${preview.noun} that has since been deleted`}
            </span>
            <span className="block text-meta text-u-text3">
              {preview.noun.charAt(0).toUpperCase() + preview.noun.slice(1)}
              {preview.version && ` · Version ${preview.version}`}
            </span>
          </span>
        </span>
      );
    case "task":
      return (
        <span className="flex items-center gap-2 text-body">
          <span className="size-[15px] flex-none rounded-sm border-[1.5px] border-u-chart-5" aria-hidden="true" />
          <span className="min-w-0">
            <span className="block truncate text-u-text">{preview.title}</span>
            {(preview.dueAt || preview.assignee) && (
              <span className="block text-note text-u-text3">
                {[shortDateTime(preview.dueAt) && `Due ${shortDateTime(preview.dueAt)}`, preview.assignee && `Assigned to ${preview.assignee}`]
                  .filter(Boolean)
                  .join(" · ")}
              </span>
            )}
          </span>
        </span>
      );
    case "origin":
      return (
        <span className="flex flex-col gap-1 text-note text-u-text3">
          {preview.door && <span>{preview.door}</span>}
          {preview.mapped && <span>Already in your candidates, so added rather than duplicated</span>}
        </span>
      );
    case "assessment":
      return <Chip tone="ai">{preview.label}</Chip>;
  }
}

function MeetingPreview({ preview }: { preview: Extract<ActivityPreview, { type: "meeting" }> }) {
  const at = new Date(preview.startsAt);
  const isValid = !Number.isNaN(at.getTime());
  return (
    <span className="flex items-center gap-2.5">
      {isValid && (
        <span className="w-10 flex-none overflow-hidden rounded-lg border border-u-border-strong bg-u-surface text-center" aria-hidden="true">
          <span className={cn("block py-0.5 text-eyebrow font-semibold uppercase tracking-[0.08em]", preview.cancelled ? "bg-u-offlimits-tint text-u-offlimits" : "bg-u-direct-tint text-u-direct")}>
            {at.toLocaleDateString("en-GB", { month: "short" })}
          </span>
          <span className="block pb-1 pt-0.5 font-mono text-body font-medium text-u-text">{at.getDate()}</span>
        </span>
      )}
      <span className="min-w-0 text-body">
        <span className={cn("block", preview.cancelled ? "text-u-text3 line-through" : "text-u-text")}>
          {shortDateTime(preview.startsAt) ?? "A call"}
        </span>
        {preview.previousStartsAt && (
          <span className="block text-note text-u-text3">Was {shortDateTime(preview.previousStartsAt)}</span>
        )}
      </span>
    </span>
  );
}

export function ChangeLine({ change }: { change: FieldChange }) {
  if (change.from === null && change.to === null) {
    return (
      <span className="text-note text-u-text2">
        <span className="text-u-text3">{fieldLabel(change.field)}</span> edited
      </span>
    );
  }
  return (
    <span className="grid grid-cols-[7.5em_minmax(0,1fr)] items-baseline gap-2 text-note">
      <span className="truncate text-u-text3">{fieldLabel(change.field)}</span>
      <span className="flex min-w-0 flex-wrap items-baseline gap-x-1.5">
        <span className="truncate text-u-text3 line-through decoration-u-offlimits">{change.from ?? "—"}</span>
        <span className="text-u-text3" aria-label="changed to">→</span>
        <span className="truncate font-medium text-u-text">{change.to ?? "Cleared"}</span>
      </span>
    </span>
  );
}

export function StatusPill({ label, isCurrent = false }: { label: string; isCurrent?: boolean }) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2 py-px text-note font-medium",
        isCurrent ? "bg-u-direct-tint text-u-direct" : "bg-u-raised text-u-text2",
      )}
    >
      {label}
    </span>
  );
}

function Chip({ children, tone }: { children: ReactNode; tone?: "ai" }) {
  return (
    <span
      className={cn(
        "inline-flex w-fit items-center rounded-full px-2 py-px text-meta font-medium",
        tone === "ai" ? "bg-u-inferred-tint text-u-inferred" : "bg-u-raised text-u-text2",
      )}
    >
      {children}
    </span>
  );
}

function clockOf(isoInstant: string): string {
  const at = new Date(isoInstant);
  return Number.isNaN(at.getTime()) ? "—" : at.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
}
