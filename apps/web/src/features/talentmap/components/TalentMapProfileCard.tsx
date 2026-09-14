import { cn } from "../../../lib/cn";
import { CandidateAvatar } from "../../candidates/components/CandidateAvatar";
import type { TreeExecutive } from "../lib/talentMapTree";

/**
 * One person as the globe draws them once it is zoomed in: face, name and title on the map itself,
 * rather than a dot that has to be hovered to say who it is. The point of the map is to read a
 * geography's people, and at city zoom there is room to simply show them.
 *
 * <p>A click selects, exactly as the dot and the panel row do, so the three stay one thing touched
 * three ways; the drawer is the popup's job, one click further in.
 */
export function TalentMapProfileCard({
  node,
  projectId,
  selected,
  hovered,
  onSelect,
  onHover,
}: {
  node: TreeExecutive;
  projectId: string;
  selected: boolean;
  hovered: boolean;
  onSelect: () => void;
  onHover: (hovering: boolean) => void;
}) {
  const { candidate } = node;
  return (
    <button
      type="button"
      onClick={onSelect}
      onMouseEnter={() => onHover(true)}
      onMouseLeave={() => onHover(false)}
      aria-pressed={selected}
      className={cn(
        "flex max-w-[186px] cursor-pointer items-center gap-1.5 rounded-full border py-1 pe-2.5 ps-1 font-sans shadow-panel backdrop-blur transition",
        selected
          ? "border-amber bg-amber-dim"
          : hovered
            ? "border-text3 bg-panel"
            : "border-line bg-panel/90 hover:border-text3",
      )}
    >
      <CandidateAvatar projectId={projectId} candidate={candidate} size="sm" />
      <span className="min-w-0 text-start">
        <span className="block truncate text-[11.5px] font-semibold leading-tight text-text">
          {candidate.fullName}
        </span>
        {candidate.title && (
          <span className="block truncate text-[10.5px] leading-tight text-text3">
            {candidate.title}
          </span>
        )}
      </span>
    </button>
  );
}
