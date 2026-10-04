import { useInfiniteQuery } from "@tanstack/react-query";
import { useState } from "react";
import { messageFor } from "../../../../lib/errorCodes";
import * as poolApi from "../../api/poolApi";
import type { TimelineGroup } from "../../api/types";
import { timelineLines } from "../../lib/candidateActivity";
import { groupByDay } from "../../lib/timelineGroups";
import { useTimelineCut } from "../PersonSections";
import { GroupChips } from "./GroupChips";

/** The Timeline tab: everything done to the person, by whom and when, on every position, by day. */
export function PersonTimelineTab({ personId }: { personId: string }) {
  const [group, setGroup] = useState<TimelineGroup | "">("");
  const timeline = useInfiniteQuery({
    queryKey: poolApi.POOL_TIMELINE_KEY(personId, group || null),
    queryFn: ({ pageParam, signal }) => poolApi.getPoolTimeline(personId, group || null, pageParam, signal),
    initialPageParam: null as number | null,
    getNextPageParam: (last) => last.nextCursor,
  });
  const entries = timeline.data?.pages.flatMap((page) => page.entries) ?? [];
  const cut = useTimelineCut(entries);

  return (
    <div className="flex flex-col gap-3">
      <GroupChips value={group} onChange={setGroup} />
      <p className="text-[12px] text-u-text3">
        Everything done to this person, by whom and when, on every position. Nothing here can be edited.
      </p>
      {timeline.isError ? (
        <p className="text-[13px] text-u-text3">{messageFor(timeline.error)}</p>
      ) : timeline.isPending ? (
        <p className="text-[13px] text-u-text3">Loading…</p>
      ) : entries.length === 0 ? (
        <p className="text-[13px] text-u-text3">Nothing of this kind recorded yet.</p>
      ) : (
        <div aria-label="Timeline">
          {groupByDay(cut.visible).map(([day, lines]) => (
            <section key={day} aria-label={day} className="mb-3">
              <h3 className="type-label mb-1.5 text-u-text3">{day}</h3>
              <ol className="flex flex-col gap-3 border-s border-dotted border-u-border-strong ps-3.5">
                {timelineLines(lines, null).map((line, index) => (
                  <li key={line.key}>
                    <p className="text-[13px]/[1.5] text-u-text2">
                      <span className="font-semibold text-u-text">{line.actorName}</span> {line.text}
                    </p>
                    <p className="mt-0.5 font-mono text-[11px] text-u-text3">
                      <time dateTime={line.occurredAt} title={new Date(line.occurredAt).toLocaleString()}>
                        {new Date(lines[index].occurredAt).toLocaleTimeString("en-GB", {
                          hour: "2-digit",
                          minute: "2-digit",
                        })}
                      </time>
                      {line.detail && ` · ${line.detail}`}
                    </p>
                  </li>
                ))}
              </ol>
            </section>
          ))}
          {cut.seeMore}
          {!cut.isClipped && timeline.hasNextPage && (
            <button
              type="button"
              onClick={() => void timeline.fetchNextPage()}
              disabled={timeline.isFetchingNextPage}
              className="text-note font-medium text-u-accent hover:underline disabled:opacity-60"
            >
              {timeline.isFetchingNextPage ? "Loading…" : "Load more"}
            </button>
          )}
        </div>
      )}
    </div>
  );
}
