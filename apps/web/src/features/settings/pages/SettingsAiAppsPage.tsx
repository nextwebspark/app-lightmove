import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { PageHeader } from "../../../components/layout/PageHeader";
import { SegmentedControl, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../../auth/AuthProvider";
import * as oauthGrantsApi from "../api/oauthGrantsApi";
import type { OAuthGrant } from "../api/types";
import { CopyableValue } from "../components/CopyableValue";
import { DisconnectAiAppModal } from "../components/DisconnectAiAppModal";
import { OAuthGrantRow } from "../components/OAuthGrantRow";
import { mcpServerUrl } from "../lib/oauthGrants";

type GrantsView = "mine" | "all";

/**
 * Settings → Connected AI apps: the AI apps reading this workspace over MCP. Connecting starts in the app, never
 * here; this page lists what was allowed and disconnects it.
 */
export function SettingsAiAppsPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const toast = useToast();
  const isAdmin = user?.workspace?.roles.includes("ADMIN") ?? false;
  const workspaceName = user?.workspace?.name ?? "the workspace";
  const [view, setView] = useState<GrantsView>("mine");
  const [disconnecting, setDisconnecting] = useState<OAuthGrant | null>(null);
  const all = isAdmin && view === "all";

  const grants = useQuery({
    queryKey: [...oauthGrantsApi.OAUTH_GRANTS_KEY, all ? "all" : "mine"],
    queryFn: ({ signal }) => oauthGrantsApi.oauthGrants(all, signal),
  });

  const disconnect = useMutation({
    mutationFn: (grant: OAuthGrant) => oauthGrantsApi.revokeOAuthGrant(grant.id),
    onSuccess: (_, grant) => {
      setDisconnecting(null);
      toast(`${grant.clientName} disconnected`);
      void queryClient.invalidateQueries({ queryKey: oauthGrantsApi.OAUTH_GRANTS_KEY });
    },
    onError: (error) => toast(messageFor(error)),
  });

  const rows = grants.data ?? [];

  return (
    <>
      <PageHeader
        title="Connected AI apps"
        subtitle="Claude, ChatGPT, Cursor and other AI apps that read Uncava over MCP. Each one asked you first, reads only what you ticked, and never changes anything."
      />

      <div className="mb-4 rounded-[10px] border border-u-border bg-u-raised px-3.5 py-3">
        <CopyableValue label="MCP server URL" value={mcpServerUrl()} />
        <span className="mt-1.5 block font-mono text-[11px]/[1.5] text-u-text3">
          Add it as a connector in your AI app. It sends you to Uncava to sign in and choose what it may read.
        </span>
      </div>

      {isAdmin && (
        <SegmentedControl
          label="Which connections"
          className="mb-3.5"
          value={view}
          onChange={setView}
          options={[
            { value: "mine", label: "Your apps" },
            { value: "all", label: `All in ${workspaceName}` },
          ]}
        />
      )}

      {grants.isError ? (
        <p className="text-[13px] text-u-text3">{messageFor(grants.error)}</p>
      ) : grants.isPending ? (
        <p className="text-[13px] text-u-text3">Loading…</p>
      ) : rows.length === 0 ? (
        <div className="rounded-[10px] border border-dashed border-u-border-strong bg-u-raised px-6 py-8 text-center">
          <span className="mx-auto mb-3 grid size-10 place-items-center rounded-[10px] bg-u-accent-tint text-u-accent">
            <Icon d={ICONS.sparkle} size={18} />
          </span>
          <div className="text-[14px] font-semibold">
            {all ? "No AI apps connected" : "You haven't connected an AI app"}
          </div>
          <div className="mx-auto mt-1.5 max-w-[440px] font-mono text-[12px]/[1.55] text-u-text3">
            {all || !isAdmin
              ? "Add the MCP server URL above as a connector in Claude, ChatGPT or Cursor. The app sends you here to sign in and choose what it may read; whatever you allow is listed on this page."
              : `Add the MCP server URL above as a connector in your AI app. Your colleagues' connections are under All in ${workspaceName}.`}
          </div>
        </div>
      ) : (
        <ul className="divide-y divide-u-border rounded-[10px] border border-u-border bg-u-raised px-4 py-1">
          {rows.map((grant) => (
            <OAuthGrantRow
              key={grant.id}
              grant={grant}
              showOwner={all}
              canDisconnect={isAdmin || grant.ownerUserId === user?.id}
              onDisconnect={() => setDisconnecting(grant)}
            />
          ))}
        </ul>
      )}

      <p className="mx-0.5 mt-3 max-w-[620px] font-mono text-[11.5px]/[1.5] text-u-text3">
        {isAdmin
          ? "An AI app reads only what its person can open today, in this workspace, and only what they ticked. It stops the moment they leave. As an admin you can see and disconnect every connection here."
          : "An AI app reads only what you can open today, in this workspace, and only what you ticked. It stops if you leave. An admin can see and disconnect every connection here."}
      </p>

      {disconnecting && (
        <DisconnectAiAppModal
          grant={disconnecting}
          ownGrant={disconnecting.ownerUserId === user?.id}
          disconnecting={disconnect.isPending}
          onConfirm={() => disconnect.mutate(disconnecting)}
          onClose={() => setDisconnecting(null)}
        />
      )}
    </>
  );
}
