import { Avatar } from "../../../components/ui/Avatar";
import type { Candidate } from "../api/types";
import { useCandidatePhoto } from "../lib/useCandidatePhoto";

/**
 * The person's face where the grid and the drawer name them — the enrichment-stored photo when one
 * exists, the product's usual initials circle otherwise.
 */
export function CandidateAvatar({
  projectId,
  candidate,
  size = "md",
  className,
}: {
  projectId: string;
  candidate: Pick<Candidate, "id" | "fullName" | "enrichedAt"> | null;
  size?: "sm" | "md" | "lg" | "xl";
  className?: string;
}) {
  const photoUrl = useCandidatePhoto(projectId, candidate);
  if (candidate === null) {
    return null;
  }
  return (
    <Avatar
      id={candidate.id}
      name={candidate.fullName}
      src={photoUrl}
      size={size}
      className={className}
    />
  );
}
