import { skipToken, useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { cn } from "../../../../lib/cn";
import { LinesSkeleton } from "../../../../components/ui/Skeleton";
import { messageFor } from "../../../../lib/errorCodes";
import * as personCrmApi from "../../api/personCrmApi";
import type { ActivityHistory } from "../../api/personCrmApi";
import * as poolApi from "../../api/poolApi";
import type { PersonDocument, PersonTimelineEntry } from "../../api/types";
import {
  actorKeyOf,
  familiesOf,
  familyOf,
  foldRuns,
  peopleOf,
  positionKeyOf,
  positionsOf,
  shortDate,
  taskStatesOf,
  type ActivityPerson,
  actorNameOf,
  actorRoleOf,
  type ActivityExecutive,
  type ActivityScope,
} from "../../lib/activityPresentation";
import { groupByDay } from "../../lib/timelineGroups";
import { ActivityDetailDialog, type ActivityDestinations } from "./ActivityDetailDialog";
import { ActivityFilterMenu, type FilterMenuOption } from "./ActivityFilterMenu";
import { ActivityCard, ActivityFold } from "./ActivityItems";
import { ActorFace, KindTile } from "./ActivityVisuals";

/**
 * A person's Activity tab, newest first under day headers, each entry a card with a face, a name and a time that
 * opens read-only. On a position it is that position's history and every change to the person; on the Candidates page
 * it is the person's changes and each position's milestones, every card naming its position. The server decides what
 * each may read; this filters, folds and draws what it was sent.
 */
export function ActivityTimeline({
  scope,
  executive,
  documents,
  destinations,
}: {
  scope: ActivityScope;
  executive: ActivityExecutive;
  documents: readonly PersonDocument[] | undefined;
  destinations: ActivityDestinations;
}) {
  const onPosition = scope.kind === "position" ? scope : null;
  const historyRead: { key: readonly unknown[]; read: (signal: AbortSignal) => Promise<ActivityHistory> } =
    scope.kind === "position"
      ? {
          key: personCrmApi.PERSON_TIMELINE_KEY(scope.projectId, scope.candidateId),
          read: (signal) => personCrmApi.getPositionHistory(scope.projectId, scope.candidateId, signal),
        }
      : { key: poolApi.PERSON_HISTORY_KEY(scope.personId), read: (signal) => poolApi.getPersonHistory(scope.personId, signal) };
  const history = useQuery({ queryKey: historyRead.key, queryFn: ({ signal }) => historyRead.read(signal) });
  const [families, setFamilies] = useState<Set<string>>(() => new Set());
  const [actors, setActors] = useState<Set<string>>(() => new Set());
  const [positions, setPositions] = useState<Set<string>>(() => new Set());
  const [openFolds, setOpenFolds] = useState<Set<string>>(() => new Set());
  const [opened, setOpened] = useState<PersonTimelineEntry | null>(null);
  const now = useMinuteClock();
  // The drawer reads the same notes for its Notes tab, so this is the cache, not a second request. The person-wide
  // history carries no note lines, so it asks for none.
  const notes = useQuery({
    queryKey: onPosition ? personCrmApi.PERSON_NOTES_KEY(onPosition.projectId, onPosition.candidateId) : ["no-notes"],
    queryFn: onPosition
      ? ({ signal }) => personCrmApi.getPersonNotes(onPosition.projectId, onPosition.candidateId, signal)
      : skipToken,
  });
  const notesById = useMemo(() => new Map((notes.data ?? []).map((note) => [note.id, note])), [notes.data]);

  const entries = useMemo(() => history.data?.entries ?? [], [history.data]);
  const byFamily = (entry: PersonTimelineEntry) => families.size === 0 || families.has(familyOf(entry));
  const byActor = (entry: PersonTimelineEntry) => actors.size === 0 || actors.has(actorKeyOf(entry));
  const byPosition = (entry: PersonTimelineEntry) => positions.size === 0 || positions.has(positionKeyOf(entry));
  const shown = entries.filter((entry) => byFamily(entry) && byActor(entry) && byPosition(entry));
  const isFiltering = families.size > 0 || actors.size > 0 || positions.size > 0;

  if (history.isPending) return <LinesSkeleton lines={6} />;
  if (history.isError) {
    return (
      <div role="alert" className="flex flex-col items-start gap-2 rounded-xl border border-u-border bg-u-bg p-4 text-note text-u-text2">
        <span>Couldn't load the activity. {messageFor(history.error)}</span>
        <button
          type="button"
          onClick={() => void history.refetch()}
          className="font-medium text-u-accent hover:underline"
        >
          Try again
        </button>
      </div>
    );
  }
  if (entries.length === 0) {
    return (
      <p className="text-note text-u-text3">
        {onPosition ? "Nothing recorded on this position yet." : "Nothing recorded for this person yet."}
      </p>
    );
  }

  const familyOptions: FilterMenuOption[] = familiesOf(entries).map((option) => ({
    key: option.family,
    label: option.label,
    visual: <KindTile icon={option.icon} size="dot" />,
    count: entries.filter((entry) => byActor(entry) && byPosition(entry) && familyOf(entry) === option.family).length,
  }));
  const people = peopleOf(entries);
  const peopleOptions: FilterMenuOption[] = people.map((person) => ({
    key: person.key,
    label: actorNameOf(person.actor, executive),
    sublabel: actorRoleOf(person.actor),
    visual: <ActorFace actor={person.actor} executive={executive} size="sm" />,
    count: entries.filter((entry) => byFamily(entry) && byPosition(entry) && actorKeyOf(entry) === person.key).length,
  }));
  const positionOptions: FilterMenuOption[] = onPosition
    ? []
    : positionsOf(entries).map((position) => ({
        key: position.key,
        label: position.label,
        visual: <KindTile icon={position.key === "person" ? ICONS.profile : ICONS.briefcase} size="dot" />,
        count: entries.filter((entry) => byFamily(entry) && byActor(entry) && positionKeyOf(entry) === position.key).length,
      }));
  const clearFilters = () => {
    setFamilies(new Set());
    setActors(new Set());
    setPositions(new Set());
  };
  const toggleFold = (key: string) =>
    setOpenFolds((open) => {
      const next = new Set(open);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  const taskStates = taskStatesOf(entries);
  const itemProps = { executive, now, onOpen: setOpened, notes: notesById, taskStates, showsPosition: !onPosition };

  return (
    <div data-activity-root className="flex flex-col">
      <div className="sticky top-0 z-20 -mx-5 flex items-center gap-1.5 border-b border-u-border bg-u-surface px-5 pb-2.5 pt-1">
        <ActivityFilterMenu
          label="Filter by kind"
          heading="Show"
          clearLabel="Show all"
          options={familyOptions}
          selected={families}
          onChange={setFamilies}
          summary={<FamilySummary selected={families} options={familyOptions} />}
          footnote="Tick several to combine, or choose Only."
        />
        <ActivityFilterMenu
          label="Filter by person"
          heading="Done by"
          clearLabel="Everyone"
          options={peopleOptions}
          selected={actors}
          onChange={setActors}
          summary={<PeopleSummary selected={actors} people={people} executive={executive} />}
        />
        {!onPosition && (
          <ActivityFilterMenu
            label="Filter by position"
            heading="Position"
            clearLabel="Every position"
            options={positionOptions}
            selected={positions}
            onChange={setPositions}
            summary={<PositionSummary selected={positions} options={positionOptions} />}
          />
        )}
        <span className="ms-auto flex flex-none items-center gap-2 font-mono text-meta text-u-text3">
          {isFiltering ? `${shown.length} of ${entries.length}` : `${entries.length} ${entries.length === 1 ? "event" : "events"}`}
          {isFiltering && (
            <button
              type="button"
              onClick={clearFilters}
              aria-label="Clear filters"
              title="Clear filters"
              className="grid size-6 place-items-center rounded-md border border-u-border bg-u-bg text-u-text2 hover:border-u-border-strong hover:text-u-text"
            >
              <Icon d={ICONS.x} size={12} />
            </button>
          )}
        </span>
      </div>

      {shown.length === 0 ? (
        <div className="mt-6 rounded-xl border border-dashed border-u-border-strong p-5 text-center text-note text-u-text3">
          {onPosition ? "Nothing matches these filters on this position." : "Nothing matches these filters."}
          <button type="button" onClick={clearFilters} className="mt-2 block w-full font-medium text-u-accent hover:underline">
            Clear filters
          </button>
        </div>
      ) : (
        <div aria-label="Activity">
          {groupByDay(shown).map(([day, lines]) => (
            <section key={day} aria-label={day} className="relative">
              <h3 className="sticky top-[45px] z-10 flex items-baseline gap-2 bg-u-surface/95 pb-2 pt-3 text-note font-semibold text-u-text backdrop-blur-sm">
                {day}
                {(day === "Today" || day === "Yesterday") && (
                  <span className="font-mono text-meta font-normal text-u-text3">{shortDate(lines[0].occurredAt)}</span>
                )}
                <span className="font-mono text-meta font-normal text-u-text3">
                  · {lines.length} {lines.length === 1 ? "event" : "events"}
                </span>
              </h3>
              <ol className="relative flex flex-col gap-2 ps-[42px] before:absolute before:start-[15px] before:top-1 before:-bottom-3 before:w-px before:bg-u-border-strong">
                {foldRuns(lines).map((item) =>
                  item.type === "fold" ? (
                    <ActivityFold
                      key={item.key}
                      entries={item.entries}
                      isOpen={openFolds.has(item.key)}
                      onToggle={() => toggleFold(item.key)}
                      {...itemProps}
                    />
                  ) : (
                    <ActivityCard key={item.entry.id} entry={item.entry} {...itemProps} />
                  ),
                )}
              </ol>
            </section>
          ))}
          <p className="flex items-center gap-2 ps-[42px] pt-4 font-mono text-meta text-u-text3">
            {history.data.truncated
              ? `Showing the latest ${entries.length} events. Older ones are kept but not shown here.`
              : isFiltering
                ? "End of the matching activity"
                : onPosition
                  ? "Start of this position's history"
                  : "Start of this person's history"}
          </p>
        </div>
      )}

      {opened && (
        <ActivityDetailDialog
          entry={opened}
          onClose={() => setOpened(null)}
          scope={scope}
          executive={executive}
          documents={documents}
          destinations={destinations}
          taskState={opened.details.taskId ? taskStates.get(opened.details.taskId) : undefined}
        />
      )}
    </div>
  );
}

function FamilySummary({ selected, options }: { selected: ReadonlySet<string>; options: readonly FilterMenuOption[] }) {
  const chosen = options.filter((option) => selected.has(option.key));
  if (chosen.length === 0) {
    return (
      <>
        <Icon d={ICONS.position} size={14} className="flex-none text-u-text3" />
        <span className="truncate">All activity</span>
      </>
    );
  }
  return (
    <>
      {chosen[0].visual}
      <span className="truncate">
        {chosen[0].label}
        {chosen.length > 1 && <span className="opacity-75"> +{chosen.length - 1}</span>}
      </span>
    </>
  );
}

function PositionSummary({ selected, options }: { selected: ReadonlySet<string>; options: readonly FilterMenuOption[] }) {
  const chosen = options.filter((option) => selected.has(option.key));
  return (
    <>
      <Icon d={ICONS.briefcase} size={14} className="flex-none text-u-text3" />
      <span className="truncate">
        {chosen.length === 0 ? "Every position" : chosen[0].label}
        {chosen.length > 1 && <span className="opacity-75"> +{chosen.length - 1}</span>}
      </span>
    </>
  );
}

function PeopleSummary({
  selected,
  people,
  executive,
}: {
  selected: ReadonlySet<string>;
  people: readonly ActivityPerson[];
  executive: ActivityExecutive;
}) {
  const chosen = people.filter((person) => selected.has(person.key));
  const faces = (chosen.length > 0 ? chosen : people).slice(0, 2);
  const label =
    chosen.length === 0
      ? "Everyone"
      : chosen.length === 1
        ? actorNameOf(chosen[0].actor, executive).split(" ")[0]
        : `${chosen.length} people`;
  return (
    <>
      <span className="flex flex-none items-center">
        {faces.map((person, index) => (
          // A flex box, not an inline span: an inline wrapper takes the text line's height and baseline, so its ring
          // drew round a box taller than the face and the photo sat low and clipped.
          <span
            key={person.key}
            className={cn(
              "flex rounded-full ring-2",
              chosen.length > 0 ? "ring-u-accent-tint" : "ring-u-surface",
              index > 0 && "-ms-1.5",
            )}
          >
            <ActorFace actor={person.actor} executive={executive} size="xs" />
          </span>
        ))}
      </span>
      <span className="truncate">{label}</span>
    </>
  );
}

/** "12 min ago" moves on while the drawer stays open. */
function useMinuteClock(): Date {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 60_000);
    return () => window.clearInterval(timer);
  }, []);
  return now;
}
