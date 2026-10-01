import { Avatar } from "../../../../components/ui/Avatar";
import { usePersonPhoto } from "../../lib/useCandidatePhoto";

/** A workspace person's face — their stored photo when research brought one, initials otherwise. */
export function PersonAvatar({
  person,
  size = "md",
  className,
}: {
  person: { personId: string; fullName: string; enrichedAt: string | null };
  size?: "sm" | "md" | "lg" | "xl";
  className?: string;
}) {
  const photoUrl = usePersonPhoto(person);
  return <Avatar id={person.personId} name={person.fullName} src={photoUrl} size={size} className={className} />;
}
