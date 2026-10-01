import { useInfiniteQuery } from "@tanstack/react-query";
import { type ReactNode, useMemo, useState } from "react";
import { Avatar } from "../../../../components/ui/Avatar";
import { Select } from "../../../../components/ui";
import { cn } from "../../../../lib/cn";
import { messageFor } from "../../../../lib/errorCodes";
import * as poolApi from "../../api/poolApi";
import type { TimelineGroup } from "../../api/types";
import { timelineLines } from "../../lib/candidateActivity";
import { usePoolLookups } from "../../lib/usePoolLookups";
import { groupByDay } from "../../lib/timelineGroups";
import { GroupChips } from "./GroupChips";

const RANGES = [
  { value: "7", label: "Last 7 days" },
  { value: "30", label: "Last 30 days" },
  { value: "365", label: "Last 12 months" },
  { value: "", label: "All time" },
] as const;

type RangeDays = (typeof RANGES)[number]["value"];

/**
 * Everything recorded across the workspace's people, newest first, by day: who did what to whom, on
 * which position. A line opens that person's drawer on their timeline.
 */
export function ActivityView({ onOpen }: { onOpen: (personId: string) => void }) {
  const lookups = usePoolLookups();
  const [actor, setActor] = useState("");
  const [position, setPosition] = useState("");
  const [range, setRange] = useState<RangeDays>("30");
  const [group, setGroup] = useState<TimelineGroup | "">("");

  const from = useMemo(() => {
    if (!range) return "";
    const start = new Date();
    start.setHours(0, 0, 0, 0);
    start.setDate(start.getDate() - Number(range));
    return start.toISOString();
  }, [range]);
  const filters: poolApi.ActivityFilters = { actor, position, group, from };

  const feed = useInfiniteQuery({
    queryKey: poolApi.ACTIVITY_FEED_KEY(filters),
    queryFn: ({ pageParam, signal }) => poolApi.getActivityFeed(filters, pageParam, signal),
    initialPageParam: null as number | null,
    getNextPageParam: (last) => last.nextCursor,
  });
  const entries = feed.data?.pages.flatMap((page) => page.entries) ?? [];

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-end gap-3">
        <InlineSelect label="Who" value={actor} onChange={setActor}>
          <option value="">Everyone</option>
          {lookups.staff.map((member) => (
            <option key={member.userId} value={member.userId}>
              {member.fullName}
            </option>
          ))}
        </InlineSelect>
        <InlineSelect label="Position" value={position} onChange={setPosition}>
          <option value="">Every position</option>
          {lookups.positions.map((project) => (
            <option key={project.id} value={project.id}>
              {project.positionTitle}
            </option>
          ))}
        </InlineSelect>
        <InlineSelect label="When" value={range} onChange={(value) => setRange(value as RangeDays)}>
          {RANGES.map((option) => (
            <option key={option.label} value={option.value}>
              {option.label}
            </option>
          ))}
        </InlineSelect>
      </div>
      <GroupChips value={group} onChange={setGroup} />

      {feed.isError ? (
        <p className="mt-4 text-[13px] text-u-text3">{messageFor(feed.error)}</p>
      ) : feed.isPending ? (
        <p className="mt-4 text-[13px] text-u-text3">Loading…</p>
      ) : entries.length === 0 ? (
        <p className="mt-4 text-[13px] text-u-text3">
          Nothing recorded for this filter. Widen the date range or pick everyone.
        </p>
      ) : (
        <div role="feed" aria-label="Candidate activity" className="mt-3 max-w-[920px]">
          {groupByDay(entries).map(([day, lines]) => (
            <section key={day} aria-label={day}>
              <h3 className="type-label sticky top-0 z-[1] bg-u-surface py-2 text-u-text3">{day}</h3>
              <ul className="flex flex-col">
                {timelineLines(lines, null, { withPerson: true }).map((line, index) => {
                  const entry = lines[index];
                  return (
                    <li key={line.key}>
                      <button
                        type="button"
                        onClick={() => onOpen(entry.personId)}
                        className="flex w-full items-start gap-3 rounded-[8px] px-2 py-2.5 text-start hover:bg-u-raised"
                      >
                        <Avatar
                          id={entry.actorUserId ?? `nobody-${entry.id}`}
                          name={line.actorName}
                          src={entry.actorAvatarUrl}
                          size="md"
                        />
                        <span className="min-w-0 flex-1">
                          <span className="block text-[13px]/[1.5] text-u-text2">
                            <span className="font-semibold text-u-text">{line.actorName}</span> {line.text}
                          </span>
                          {line.detail && (
                            <span
                              className={cn(
                                "mt-0.5 block font-mono text-[11px] text-u-text3",
                                entry.noteExcerpt && "border-s-2 border-u-border-strong ps-2",
                              )}
                            >
                              {line.detail}
                            </span>
                          )}
                        </span>
                        <time
                          dateTime={entry.occurredAt}
                          title={new Date(entry.occurredAt).toLocaleString()}
                          className="flex-none font-mono text-[11px] text-u-text3"
                        >
                          {new Date(entry.occurredAt).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" })}
                        </time>
                      </button>
                    </li>
                  );
                })}
              </ul>
            </section>
          ))}
          {feed.hasNextPage && (
            <button
              type="button"
              onClick={() => void feed.fetchNextPage()}
              disabled={feed.isFetchingNextPage}
              className="mt-3 text-note font-medium text-u-accent hover:underline disabled:opacity-60"
            >
              {feed.isFetchingNextPage ? "Loading…" : "Load older activity"}
            </button>
          )}
        </div>
      )}
    </div>
  );
}

function InlineSelect({
  label,
  value,
  onChange,
  children,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  children: ReactNode;
}) {
  return (
    <label className="flex items-center gap-2">
      <span className="type-label text-u-text3">{label}</span>
      <Select value={value} onChange={(event) => onChange(event.target.value)} className="w-auto text-[13px]">
        {children}
      </Select>
    </label>
  );
}
