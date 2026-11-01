import { useRef, useState } from "react";
import { Avatar, Button } from "../../../components/ui";
import { formatMonthYear, titleCase } from "../../../lib/format";
import type { WorkspaceRole } from "../../auth/api/types";

/**
 * Who the caller is, as the workspace sees them: the identity tile, their name, the standing that is not theirs to
 * edit, and their photo — a provider's until they upload their own, which no later sign-in replaces.
 */
export function ProfileIdentityRow({
  userId,
  fullName,
  avatarUrl,
  roles,
  joinedAt,
  photoUploaded,
  onPhotoChosen,
  onPhotoRemoved,
}: {
  userId: string;
  fullName: string;
  avatarUrl: string | null;
  roles: WorkspaceRole[];
  joinedAt: string | null;
  photoUploaded: boolean;
  onPhotoChosen: (file: File) => Promise<void>;
  onPhotoRemoved: () => Promise<void>;
}) {
  const fileInput = useRef<HTMLInputElement>(null);
  const [isBusy, setIsBusy] = useState(false);

  const handleChosen = async (file: File | undefined) => {
    if (!file) return;
    setIsBusy(true);
    try {
      await onPhotoChosen(file);
    } finally {
      setIsBusy(false);
      if (fileInput.current) fileInput.current.value = "";
    }
  };

  const handleRemove = async () => {
    setIsBusy(true);
    try {
      await onPhotoRemoved();
    } finally {
      setIsBusy(false);
    }
  };

  return (
    <div className="mb-5 flex flex-wrap items-center gap-3.5">
      <Avatar id={userId} name={fullName} src={avatarUrl} size="xl" className="rounded-xl" />
      <div className="min-w-0 flex-1">
        <div className="text-sm font-semibold">{fullName}</div>
        <div className="mt-0.5 font-mono text-[11.5px] text-u-text3">{membershipMeta(roles, joinedAt)}</div>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <input
          ref={fileInput}
          type="file"
          accept="image/png,image/jpeg"
          className="hidden"
          aria-label="Photo file"
          onChange={(event) => void handleChosen(event.target.files?.[0])}
        />
        <Button variant="secondary" size="sm" loading={isBusy} onClick={() => fileInput.current?.click()}>
          Change photo
        </Button>
        {photoUploaded && (
          <Button variant="ghost" size="sm" disabled={isBusy} onClick={() => void handleRemove()}>
            Remove
          </Button>
        )}
      </div>
    </div>
  );
}

/**
 * "Admin · joined Mar 2026" — and the roles alone when the join date is missing, rather than a dash
 * mid-sentence. A member may hold more than one role, so this reads them all.
 */
function membershipMeta(roles: WorkspaceRole[], joinedAt: string | null): string {
  const standing = roles.map(titleCase).join(", ");
  const joined = formatMonthYear(joinedAt);
  return joined ? `${standing} · joined ${joined}` : standing;
}
