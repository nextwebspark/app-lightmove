import { useQueryClient } from "@tanstack/react-query";
import { PageHeader } from "../../../components/layout/PageHeader";
import { useToast } from "../../../components/ui";
import { useAuth } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";
import { messageFor } from "../../../lib/errorCodes";
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
      phone: values.phone,
      linkedinUrl: values.linkedinUrl,
    });
    // The topbar avatar and the roster read the same name, and reload() re-mints the token first.
    await reload();
    // The mailbox read carries the same zone, which the composer, Add to sequence and the sequence editor show.
    void queryClient.invalidateQueries({ queryKey: mailboxApi.MAILBOX_KEY });
    toast("Profile saved");
  };

  const handlePhoto = async (file: File) => {
    try {
      await authApi.uploadPhoto(file);
      await reload();
      toast("Photo updated");
    } catch (error) {
      toast.error(messageFor(error));
    }
  };

  const handleRemovePhoto = async () => {
    try {
      await authApi.removePhoto();
      await reload();
      toast("Photo removed");
    } catch (error) {
      toast.error(messageFor(error));
    }
  };

  return (
    <>
      <PageHeader title="Profile" subtitle="How you appear across the workspace" />

      <div className="rounded-[10px] border border-u-border bg-u-raised p-5">
        <ProfileIdentityRow
          userId={user.id}
          fullName={user.fullName}
          avatarUrl={user.avatarUrl}
          roles={user.workspace?.roles ?? []}
          joinedAt={user.workspace?.joinedAt ?? null}
          photoUploaded={user.photoUploaded}
          onPhotoChosen={handlePhoto}
          onPhotoRemoved={handleRemovePhoto}
        />

        <ProfileForm
          email={user.email}
          roles={user.workspace?.roles ?? []}
          defaultValues={{
            fullName: user.fullName,
            title: user.title ?? "",
            timezone: user.timezone,
            locale: user.locale,
            phone: user.phone ?? "",
            linkedinUrl: user.linkedinUrl ?? "",
          }}
          onSave={handleSave}
        />
      </div>
    </>
  );
}
