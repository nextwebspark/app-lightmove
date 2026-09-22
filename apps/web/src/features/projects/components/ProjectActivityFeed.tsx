import { useQuery } from "@tanstack/react-query";
import { Avatar } from "../../../components/ui";
import { formatRelativeTime } from "../../../lib/format";
import * as projectsApi from "../api/projectsApi";

/**
 * What has happened on the mandate lately, read back out of the audit trail.
 *
 * <p>Fetched only when the drawer opens, and refused for a client contact — the feed narrates the
 * firm's own research. A refusal renders nothing rather than an empty state: "no activity" would
 * state as fact something the caller was not allowed to read.
 */
export function ProjectActivityFeed({ projectId }: { projectId: string }) {
  const { data: activity, isPending, isError } = useQuery({
    queryKey: projectsApi.projectActivityKey(projectId),
    queryFn: ({ signal }) => projectsApi.projectActivity(projectId, signal),
    staleTime: 30 * 1000,
  });

  // Nothing at all until the read lands. A client representative is refused this — the feed narrates
  // the firm's own research — so a heading and a "Loading…" that collapse into a 403 would announce a
  // section they are never going to see.
  if (isError || isPending) return null;

  return (
    <>
      <div className="mb-2 mt-[18px] font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-text3">
        Recent activity
      </div>

      {activity.length === 0 ? (
        <p className="font-mono text-[11px] text-text3">Nothing has happened on this mandate yet</p>
      ) : (
        <ul className="border-l border-line-soft">
          {activity.map((line) => (
            <li
              key={`${line.occurredAt}-${line.eventType}-${line.summary}`}
              className="flex items-center gap-2.5 py-[7px] pl-3"
            >
              <span className="min-w-0 flex-1">
                <span className="block truncate text-[12.5px] font-medium text-text">
                  {line.actorName} {line.summary}
                </span>
                <span className="mt-0.5 block font-mono text-[11px] text-text3">
                  {formatRelativeTime(line.occurredAt)}
                </span>
              </span>
              <Avatar id={line.actorName} name={line.actorName} src={line.actorAvatarUrl} size="sm" />
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
