import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Button, SegmentedControl, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../../auth/AuthProvider";
import * as apiKeysApi from "../api/apiKeysApi";
import type { ApiKey, CreateApiKeyRequest } from "../api/types";
import { ApiKeyRow } from "../components/ApiKeyRow";
import { CreateApiKeyModal } from "../components/CreateApiKeyModal";
import { RevealApiKeyModal } from "../components/RevealApiKeyModal";
import { RevokeApiKeyModal } from "../components/RevokeApiKeyModal";

const DOCS_URL = "/api/v1/public/docs";

type KeysView = "mine" | "all";

interface RevealedKey {
  name: string;
  secret: string;
}

/** Settings → API keys. The secret is held in state only while its dialog is open, never in a cache or storage. */
export function SettingsApiKeysPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const toast = useToast();
  const isAdmin = user?.workspace?.roles.includes("ADMIN") ?? false;
  const [view, setView] = useState<KeysView>("mine");
  const [creating, setCreating] = useState(false);
  const [revealed, setRevealed] = useState<RevealedKey | null>(null);
  const [revoking, setRevoking] = useState<ApiKey | null>(null);
  const all = isAdmin && view === "all";

  const keys = useQuery({
    queryKey: [...apiKeysApi.API_KEYS_KEY, all ? "all" : "mine"],
    queryFn: ({ signal }) => apiKeysApi.apiKeys(all, signal),
  });
  const refresh = () => void queryClient.invalidateQueries({ queryKey: apiKeysApi.API_KEYS_KEY });

  // Set here, not in onSuccess: the mutation returns only the key, so its cache never holds the secret.
  const create = useMutation({
    mutationFn: async (request: CreateApiKeyRequest) => {
      const created = await apiKeysApi.createApiKey(request);
      setRevealed({ name: created.key.name, secret: created.secret });
      return created.key;
    },
    onSuccess: (key) => {
      setCreating(false);
      if (key.kind === "SERVICE") setView("all");
      refresh();
    },
  });

  const revoke = useMutation({
    mutationFn: (key: ApiKey) => apiKeysApi.revokeApiKey(key.id),
    onSuccess: (_, key) => {
      setRevoking(null);
      toast(`${key.name} revoked`);
      refresh();
    },
    onError: (error) => toast(messageFor(error)),
  });

  const handleOpenCreate = () => {
    create.reset();
    setCreating(true);
  };

  const rows = keys.data ?? [];
  const createButton = (
    <Button onClick={handleOpenCreate} className="whitespace-nowrap px-3.5 py-[7px] text-[13px]">
      <Icon d={ICONS.plus} size={15} />
      Create key
    </Button>
  );

  return (
    <>
      <PageHeader
        title="API keys"
        subtitle="Connect an ATS, a BI tool or a script to Uncava's public API. A key reads positions, their companies and their executives as JSON. It never changes anything."
        action={keys.isSuccess && rows.length > 0 ? createButton : undefined}
      />

      {isAdmin && (
        <SegmentedControl
          label="Which keys"
          className="mb-3.5"
          value={view}
          onChange={setView}
          options={[
            { value: "mine", label: "Your keys" },
            { value: "all", label: `All keys in ${user?.workspace?.name ?? "the workspace"}` },
          ]}
        />
      )}

      {keys.isError ? (
        <p className="text-[13px] text-u-text3">{messageFor(keys.error)}</p>
      ) : keys.isPending ? (
        <p className="text-[13px] text-u-text3">Loading…</p>
      ) : rows.length === 0 ? (
        <div className="rounded-[10px] border border-dashed border-u-border-strong bg-u-raised px-6 py-8 text-center">
          <span className="mx-auto mb-3 grid size-10 place-items-center rounded-[10px] bg-u-accent-tint text-u-accent">
            <Icon d={ICONS.key} size={18} />
          </span>
          <div className="text-[14px] font-semibold">No API keys yet</div>
          <div className="mx-auto mt-1.5 max-w-[440px] font-mono text-[12px]/[1.55] text-u-text3">
            Create a key for each tool that reads from Uncava, so you can revoke one without breaking the others. A key
            reads positions, companies and executives; it never changes anything.
          </div>
          <div className="mt-4 flex flex-wrap items-center justify-center gap-3.5">
            {createButton}
            <a href={DOCS_URL} target="_blank" rel="noreferrer" className="text-[12.5px] font-medium text-u-accent">
              Read the API docs ↗
            </a>
          </div>
        </div>
      ) : (
        <ul className="divide-y divide-u-border rounded-[10px] border border-u-border bg-u-raised px-4 py-1">
          {rows.map((key) => (
            <ApiKeyRow
              key={key.id}
              apiKey={key}
              showOwner={all}
              canRevoke={isAdmin || key.ownerUserId === user?.id}
              onRevoke={() => setRevoking(key)}
            />
          ))}
        </ul>
      )}

      <p className="mx-0.5 mt-3 max-w-[620px] font-mono text-[11.5px]/[1.5] text-u-text3">
        {isAdmin
          ? "A personal key reads only the positions its owner can open, and stops working the moment they leave the workspace. A workspace key reads every position and outlives the person who made it; only an admin can make or revoke one."
          : "A key reads only the positions you can open, and stops working if you leave the workspace. An admin can see and revoke every key here."}{" "}
        <a href={DOCS_URL} target="_blank" rel="noreferrer" className="text-u-accent">
          API docs ↗
        </a>
      </p>

      {creating && (
        <CreateApiKeyModal
          canMakeWorkspaceKeys={isAdmin}
          workspaceName={user?.workspace?.name ?? "the workspace"}
          creating={create.isPending}
          error={create.error}
          onCreate={(request) => create.mutate(request)}
          onClose={() => setCreating(false)}
        />
      )}
      {revealed && (
        <RevealApiKeyModal name={revealed.name} secret={revealed.secret} onDone={() => setRevealed(null)} />
      )}
      {revoking && (
        <RevokeApiKeyModal
          apiKey={revoking}
          ownKey={revoking.ownerUserId === user?.id}
          revoking={revoke.isPending}
          onConfirm={() => revoke.mutate(revoking)}
          onClose={() => setRevoking(null)}
        />
      )}
    </>
  );
}
