import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Avatar, Button, Toggle, useToast } from "../../../components/ui";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import { messageFor } from "../../../lib/errorCodes";
import { formatInstantDate } from "../../../lib/format";
import { WORKSPACE_KEY } from "../../workspace/api/workspaceApi";
import type { MemberTwoFactor } from "../api/types";
import {
  changeRequireMfa,
  resetMemberTwoFactor,
  WORKSPACE_TWO_FACTOR_KEY,
  workspaceTwoFactor,
} from "../api/workspaceTwoFactorApi";

/**
 * Settings → Security for a workspace admin: whether every member must hold two-factor authentication, who in the
 * workspace has it, and a reset for a member who lost their phone and their recovery codes.
 */
export function WorkspaceTwoFactorCard() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const overview = useQuery({ queryKey: WORKSPACE_TWO_FACTOR_KEY, queryFn: () => workspaceTwoFactor() });
  const [resetting, setResetting] = useState<MemberTwoFactor | null>(null);

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: WORKSPACE_TWO_FACTOR_KEY });
    void queryClient.invalidateQueries({ queryKey: WORKSPACE_KEY });
  };

  const requirement = useMutation({
    mutationFn: (required: boolean) => changeRequireMfa(required),
    onSuccess: (_, required) => {
      refresh();
      toast(required ? "Two-factor authentication is now required" : "Two-factor authentication is no longer required");
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  const reset = useMutation({
    mutationFn: (member: MemberTwoFactor) => resetMemberTwoFactor(member.memberId),
    onSuccess: (_, member) => {
      setResetting(null);
      refresh();
      toast(`Two-factor authentication reset for ${member.fullName}`);
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  const required = overview.data?.required ?? false;
  const offered = overview.data?.offered ?? false;
  const members = overview.data?.members ?? [];
  const enrolled = members.filter((member) => member.enabled).length;

  return (
    <div className="rounded-u-card border border-u-border bg-u-raised p-5">
      <div className="flex items-center gap-3">
        <div className="flex-1">
          <div className="text-body font-semibold">Require two-factor authentication</div>
          <div className="mt-1 font-mono text-meta text-u-text3">
            Everyone in this workspace, clients included, sets it up before they can open anything.
          </div>
        </div>
        {overview.isSuccess && !offered && (
          <span className="font-mono text-meta text-u-text3">Not available yet</span>
        )}
        <Toggle
          checked={required}
          disabled={!overview.isSuccess || requirement.isPending || (!required && !offered)}
          label="Require two-factor authentication"
          onChange={(checked) => requirement.mutate(checked)}
        />
      </div>

      {overview.isError && (
        <p className="mt-3 font-mono text-meta text-u-text3">Couldn't load who has two-factor authentication.</p>
      )}

      {overview.isSuccess && members.length > 0 && (
        <div className="mt-4 border-t border-u-border pt-3">
          <p className="mb-2 font-mono text-meta text-u-text3">
            {enrolled} of {members.length} {members.length === 1 ? "member has" : "members have"} it on
          </p>
          <ul className="flex flex-col">
            {members.map((member) => (
              <li key={member.memberId} className="flex items-center gap-3 py-1.5">
                <Avatar id={member.userId} name={member.fullName} src={member.avatarUrl} size="sm" />
                <div className="min-w-0 flex-1">
                  <div className="truncate text-note text-u-text">{member.fullName}</div>
                  <div className="truncate font-mono text-meta text-u-text3">{member.email}</div>
                </div>
                <span className="font-mono text-meta text-u-text3">
                  {member.enabled ? `On since ${formatInstantDate(member.enabledAt)}` : "Off"}
                </span>
                {member.enabled && member.resettable && (
                  <Button size="xs" variant="secondary" onClick={() => setResetting(member)}>
                    Reset
                  </Button>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}

      <ConfirmDialog
        open={resetting !== null}
        title="Reset two-factor authentication?"
        confirmLabel="Reset"
        pending={reset.isPending}
        onConfirm={() => resetting && reset.mutate(resetting)}
        onClose={() => setResetting(null)}
      >
        <p>
          {resetting?.fullName} will be signed out everywhere and emailed. Their authenticator app and recovery codes stop
          working{required ? ", and they set two-factor up again when they next sign in" : ""}.
        </p>
      </ConfirmDialog>
    </div>
  );
}
