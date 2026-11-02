import { useQueryClient } from "@tanstack/react-query";
import { PageHeader } from "../../../components/layout/PageHeader";
import { useToast } from "../../../components/ui";
import { useAuth } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";
import * as mailboxApi from "../../outreach/api/mailboxApi";
import { ProfileForm } from "../components/ProfileForm";
import { ProfileIdentityRow } from "../components/ProfileIdentityRow";
import type { ProfileValues } from "../lib/profileSchema";

/** Settings → Profile: how the caller appears across the workspace. */
export function SettingsProfilePage() {
  const { user, reload } = useAuth();
  const toast = useToast();
  const queryClient = useQueryClient();

  // RequireWorkspace has already resolved the session; this only satisfies the type.
  if (!user) return null;

  const handleSave = async (values: ProfileValues) => {
    await authApi.updateProfile({
      fullName: values.fullName,
      // An empty box is "no title", which the contract spells null rather than "".
      title: values.title === "" ? null : values.title,
      timezone: values.timezone,
      locale: values.locale,
    });
    // The topbar avatar and the roster read the same name, and reload() re-mints the token first.
    await reload();
    // The mailbox read carries the same zone, which the composer, Add to sequence and the sequence editor show.
    void queryClient.invalidateQueries({ queryKey: mailboxApi.MAILBOX_KEY });
    toast("Profile saved");
  };

  // The change is saved by now: what follows only redraws it. The photo is drawn on most screens — rosters, teams,
  // tasks, notes — so every cached read is marked stale (`refetchType: "none"`) and refetched where it is next shown,
  // rather than naming each one here and missing the next; the session reload redraws the topbar and this page.
  const afterPhotoChange = (message: string) => {
    toast(message);
    void queryClient.invalidateQueries({ refetchType: "none" });
    void reload();
  };

  /** Resolves once the upload has: the dialog closes on a saved photo, whatever the redraw meets. */
  const handleSavePhoto = async (photo: Blob) => {
    await authApi.uploadAvatar(photo);
    afterPhotoChange("Profile photo saved");
  };

  const handleRemovePhoto = async () => {
    try {
      await authApi.removeAvatar();
    } catch {
      toast.error("Could not remove your photo. Try again.", { retry: () => void handleRemovePhoto() });
      return;
    }
    afterPhotoChange("Profile photo removed");
  };

  return (
    <>
      <PageHeader title="Profile" subtitle="How you appear across the workspace" />

      <div className="rounded-[10px] border border-u-border bg-u-raised p-5">
        <ProfileIdentityRow
          userId={user.id}
          fullName={user.fullName}
          avatarUrl={user.avatarUrl}
          avatarEditable={user.avatarEditable}
          roles={user.workspace?.roles ?? []}
          joinedAt={user.workspace?.joinedAt ?? null}
          onSavePhoto={handleSavePhoto}
          onRemovePhoto={handleRemovePhoto}
        />

        <ProfileForm
          email={user.email}
          roles={user.workspace?.roles ?? []}
          defaultValues={{
            fullName: user.fullName,
            title: user.title ?? "",
            timezone: user.timezone,
            locale: user.locale,
          }}
          onSave={handleSave}
        />
      </div>
    </>
  );
}
