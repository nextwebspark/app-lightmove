import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Button, Field, FormError, Input, Modal, Select, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../../auth/AuthProvider";
import * as workspaceApi from "../../workspace/api/workspaceApi";

const REGIONS = ["GCC", "MENA", "Europe", "Global"];
const CURRENCIES = ["USD", "AED", "SAR", "EUR"];

/** Settings → General: identity card, name/defaults form, and the typed-confirmation danger zone. */
export function SettingsGeneralPage() {
  const { reload } = useAuth();
  const queryClient = useQueryClient();
  const toast = useToast();

  const { data: workspace } = useQuery({
    queryKey: workspaceApi.WORKSPACE_KEY,
    queryFn: workspaceApi.workspace,
  });

  const [name, setName] = useState("");
  const [region, setRegion] = useState("GCC");
  const [currency, setCurrency] = useState("USD");
  const [deleteOpen, setDeleteOpen] = useState(false);

  useEffect(() => {
    if (workspace) {
      setName(workspace.name);
      setRegion(workspace.defaultRegion);
      setCurrency(workspace.defaultCurrency);
    }
  }, [workspace]);

  const save = useMutation({
    mutationFn: () =>
      workspaceApi.updateWorkspace({ name: name.trim(), defaultRegion: region, defaultCurrency: currency }),
    onSuccess: async () => {
      void queryClient.invalidateQueries({ queryKey: workspaceApi.WORKSPACE_KEY });
      // The name and logo mark also live in the auth summary the topbar reads.
      await reload();
      toast("Workspace settings saved");
    },
    onError: (error) => toast(messageFor(error)),
  });

  if (!workspace) return null;

  return (
    <>
      <PageHeader title="General" subtitle="Workspace identity and defaults" />

      <div className="rounded-[10px] border border-line-soft bg-panel2 p-5">
        <div className="mb-5 flex items-center gap-3.5">
          <span className="grid size-11 place-items-center rounded-[11px] bg-amber-btn font-mono text-lg font-bold text-on-amber">
            {workspace.logoMark ?? workspace.name[0]}
          </span>
          <div>
            <div className="text-sm font-semibold">{workspace.name}</div>
            <div className="mt-0.5 font-mono text-[11.5px] text-text3">
              {workspace.plan.toLowerCase()} plan · {workspace.memberCount}{" "}
              {workspace.memberCount === 1 ? "member" : "members"}
            </div>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-x-4 gap-y-3.5">
          <Field label="Workspace name">
            <Input value={name} onChange={(event) => setName(event.target.value)} className="!bg-panel" />
          </Field>
          <Field label="Workspace URL">
            <div className="rounded-lg border border-line-soft bg-panel px-3 py-[9px] font-mono text-[13px] font-medium text-text2">
              lightmove.app/w/{workspace.slug}
            </div>
          </Field>
          <Field label="Default region">
            <Select value={region} onChange={(event) => setRegion(event.target.value)} className="!bg-panel">
              {REGIONS.map((r) => (
                <option key={r}>{r}</option>
              ))}
            </Select>
          </Field>
          <Field label="Default currency">
            <Select value={currency} onChange={(event) => setCurrency(event.target.value)} className="!bg-panel">
              {CURRENCIES.map((c) => (
                <option key={c}>{c}</option>
              ))}
            </Select>
          </Field>
        </div>

        <div className="mt-5 flex justify-end">
          <Button loading={save.isPending} disabled={!name.trim()} onClick={() => save.mutate()}>
            Save changes
          </Button>
        </div>
      </div>

      <div className="mt-4 rounded-[10px] border border-red bg-red-dim p-5">
        <div className="flex items-center gap-3">
          <div className="flex-1">
            <div className="text-[13px] font-semibold text-red">Delete workspace</div>
            <div className="mt-1 font-mono text-[11.5px] text-text3">
              Permanently removes all projects, candidates and client records. This cannot be undone.
            </div>
          </div>
          <Button
            variant="secondary"
            className="!border-red !text-red hover:!bg-red hover:!text-white"
            onClick={() => setDeleteOpen(true)}
          >
            Delete…
          </Button>
        </div>
      </div>

      {deleteOpen && (
        <DeleteWorkspaceModal workspaceName={workspace.name} onClose={() => setDeleteOpen(false)} />
      )}
    </>
  );
}

/**
 * The typed-name confirmation. The exact match enabling the button is browser UX; the server
 * verifies the same string again — this dialog is not the guard rail, only its handle.
 */
function DeleteWorkspaceModal({ workspaceName, onClose }: { workspaceName: string; onClose: () => void }) {
  const { reload } = useAuth();
  const [confirmName, setConfirmName] = useState("");
  const [error, setError] = useState<string | null>(null);

  const destroy = useMutation({
    mutationFn: () => workspaceApi.deleteWorkspace(confirmName),
    // reload() re-mints the token without a workspace claim; the router then lands on the wizard.
    onSuccess: () => reload(),
    onError: (mutationError) => setError(messageFor(mutationError)),
  });

  const matches = confirmName.trim().toLowerCase() === workspaceName.toLowerCase();

  return (
    <Modal open onClose={onClose} title="Delete workspace">
      <FormError message={error} />
      <p className="mb-4 text-[13px] text-text2">
        This removes every member and cancels outstanding invitations. Type{" "}
        <b className="font-semibold text-text">{workspaceName}</b> to confirm.
      </p>

      <Field label="Workspace name">
        <Input
          value={confirmName}
          onChange={(event) => setConfirmName(event.target.value)}
          placeholder={workspaceName}
          autoFocus
        />
      </Field>

      <div className="mt-5 flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>
          Cancel
        </Button>
        <Button
          className="!border-red !bg-red !text-white hover:!brightness-105"
          disabled={!matches}
          loading={destroy.isPending}
          onClick={() => destroy.mutate()}
        >
          Delete workspace
        </Button>
      </div>
    </Modal>
  );
}
