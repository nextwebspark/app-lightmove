import { lazy, Suspense, useRef, useState } from "react";
import { Avatar, Button } from "../../../components/ui";
import { formatMonthYear, titleCase } from "../../../lib/format";
import type { WorkspaceRole } from "../../auth/api/types";

const ProfilePhotoDialog = lazy(() => import("./ProfilePhotoDialog"));

/** The browser decodes the picture and the dialog re-draws it as a JPEG, so anything it can open will do. */
const PHOTO_ACCEPT = "image/jpeg,image/png,image/webp,image/heic,image/heif";

/**
 * Who the caller is, as the workspace sees them: the identity tile, their name, the standing that is not theirs to
 * edit, and — unless the workspace's identity provider supplies it — Change photo and Remove photo.
 */
export function ProfileIdentityRow({
  userId,
  fullName,
  avatarUrl,
  avatarEditable,
  roles,
  joinedAt,
  onSavePhoto,
  onRemovePhoto,
}: {
  userId: string;
  fullName: string;
  avatarUrl: string | null;
  avatarEditable: boolean;
  roles: WorkspaceRole[];
  joinedAt: string | null;
  onSavePhoto: (photo: Blob) => Promise<void>;
  onRemovePhoto: () => Promise<void>;
}) {
  const fileInput = useRef<HTMLInputElement>(null);
  const [picked, setPicked] = useState<File | null>(null);
  const [removing, setRemoving] = useState(false);

  const handleRemove = async () => {
    setRemoving(true);
    try {
      await onRemovePhoto();
    } finally {
      setRemoving(false);
    }
  };

  return (
    <div className="mb-5 flex flex-wrap items-center gap-3.5">
      <Avatar id={userId} name={fullName} src={avatarUrl} size="xl" className="rounded-xl" />
      <div className="min-w-40 flex-1">
        <div className="text-sm font-semibold">{fullName}</div>
        <div className="mt-0.5 font-mono text-[11.5px] text-u-text3">{membershipMeta(roles, joinedAt)}</div>
      </div>
      {avatarEditable ? (
        <div className="flex items-center gap-2">
          <input
            ref={fileInput}
            type="file"
            accept={PHOTO_ACCEPT}
            aria-label="Choose a profile photo"
            className="hidden"
            onChange={(event) => {
              setPicked(event.target.files?.[0] ?? null);
              // Cleared so picking the same file again after Cancel still opens the dialog.
              event.target.value = "";
            }}
          />
          <Button variant="secondary" size="sm" onClick={() => fileInput.current?.click()}>
            Change photo
          </Button>
          {avatarUrl && (
            <Button variant="ghost" size="sm" loading={removing} onClick={handleRemove}>
              Remove photo
            </Button>
          )}
        </div>
      ) : (
        <div className="font-mono text-meta text-u-text3">Your photo comes from your organisation's sign-in</div>
      )}
      {picked && (
        <Suspense fallback={null}>
          <ProfilePhotoDialog
            file={picked}
            onClose={() => setPicked(null)}
            onSave={async (photo) => {
              await onSavePhoto(photo);
              setPicked(null);
            }}
          />
        </Suspense>
      )}
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
