import { useState } from "react";
import { DetailPill } from "../../../components/ui/DetailList";
import { cn } from "../../../lib/cn";
import type { CandidateCareerEntry } from "../api/types";
import { groupCareer, isCurrent, parsePeriod, tenureOf } from "../lib/careerTimeline";

const VISIBLE_POSTS = 4;

/**
 * A career as a timeline: consecutive posts at one employer nest under it, the open-ended one is
 * flagged, and a tenure is worked out beside any period the text allows. Long histories show four
 * posts and offer the rest — enrichment routinely brings fifteen, and the reader came for the recent
 * ones.
 */
export function CareerTimeline({ career }: { career: readonly CandidateCareerEntry[] }) {
  const [showAll, setShowAll] = useState(false);
  if (career.length === 0) {
    return <p className="font-mono text-[12.5px] text-text3">No history captured yet.</p>;
  }
  const shown = showAll ? career : career.slice(0, VISIBLE_POSTS);
  const groups = groupCareer(shown);

  return (
    <>
      <ol className="ms-[5px] border-s border-line ps-4">
        {groups.map((group, index) => {
          const current = group.posts.some((post) => isCurrent(post.period));
          return (
            <li key={`${group.company}-${index}`} className="relative pb-4 last:pb-0">
              <span
                aria-hidden="true"
                className={cn(
                  "absolute -start-[21px] top-[5px] size-[9px] rounded-full border-2 border-panel",
                  current ? "bg-sky ring-2 ring-sky-dim" : "bg-line",
                )}
              />
              <div className="font-sans text-[13px] font-semibold text-text">
                {group.company ?? "Employer not recorded"}
              </div>
              {group.posts.length === 1 ? (
                <CareerPost post={group.posts[0]} />
              ) : (
                <ol className="mt-1.5 border-s border-line-soft ps-3.5">
                  {group.posts.map((post, postIndex) => (
                    <li key={`${post.title}-${post.period}-${postIndex}`} className="pb-2.5 last:pb-0">
                      <CareerPost post={post} />
                    </li>
                  ))}
                </ol>
              )}
            </li>
          );
        })}
      </ol>
      {career.length > VISIBLE_POSTS && (
        <button
          type="button"
          onClick={() => setShowAll((current) => !current)}
          className="mt-3 font-mono text-[11px] font-semibold uppercase tracking-[0.06em] text-sky transition hover:underline"
        >
          {showAll ? "Show fewer" : `Show all ${career.length} posts`}
        </button>
      )}
    </>
  );
}

function CareerPost({ post }: { post: CandidateCareerEntry }) {
  const tenure = tenureOf(parsePeriod(post.period));
  const current = isCurrent(post.period);
  return (
    <div>
      {post.title && (
        <div className="mt-0.5 font-sans text-[13px] font-medium text-text2">{post.title}</div>
      )}
      {(post.period || current) && (
        <div className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-1 font-mono text-[11.5px] text-text3">
          {post.period && <span>{post.period}</span>}
          {tenure && <span>· {tenure}</span>}
          {current && <DetailPill label="Current" className="bg-green-dim text-green" />}
        </div>
      )}
    </div>
  );
}
