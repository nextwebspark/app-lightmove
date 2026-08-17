import { PageHeader } from "../../../components/layout/PageHeader";
import { useToast } from "../../../components/ui";
import { useAuth } from "../../auth/AuthProvider";
import * as authApi from "../../auth/api/authApi";
import { ProfileForm } from "../components/ProfileForm";
import { ProfileIdentityRow } from "../components/ProfileIdentityRow";
import type { ProfileValues } from "../lib/profileSchema";

/** Settings → Profile: how the caller appears across the workspace. */
export function SettingsProfilePage() {
  const { user, reload } = useAuth();
  const toast = useToast();

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
    toast("Profile saved");
  };

  return (
    <>
      <PageHeader title="Profile" subtitle="How you appear across the workspace" />

      <div className="rounded-[10px] border border-line-soft bg-panel2 p-5">
        <ProfileIdentityRow
          userId={user.id}
          fullName={user.fullName}
          avatarUrl={user.avatarUrl}
          roles={user.workspace?.roles ?? []}
          joinedAt={user.workspace?.joinedAt ?? null}
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
