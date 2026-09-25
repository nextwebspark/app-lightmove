import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Button, Field, FormError, Input, Modal, Select, useToast } from "../../../components/ui";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import { CURRENCIES, DEFAULT_CURRENCY } from "../../../lib/currencies";
import { messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../../auth/AuthProvider";
import type { WorkspaceCompany } from "../../auth/api/types";
import { CompanyPicker } from "../../clients/components/CompanyPicker";
import {
  pickedCompanyName,
  workspaceCompanyPick,
  type CompanyPick,
} from "../../clients/lib/companyPick";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import { WorkspacePersonaCard } from "../components/WorkspacePersonaCard";

const REGIONS = ["GCC", "MENA", "Europe", "Global"];

/**
 * Settings → General: identity card, the firm (picked from the universe, as at signup) and defaults,
 * and the typed-confirmation danger zone.
 */
export function SettingsGeneralPage() {
  const { reload } = useAuth();
  const queryClient = useQueryClient();
  const toast = useToast();

  const { data: workspace } = useQuery({
    queryKey: workspaceApi.WORKSPACE_KEY,
    queryFn: workspaceApi.workspace,
  });

  const [pick, setPick] = useState<CompanyPick | null>(null);
  const [region, setRegion] = useState("GCC");
  const [currency, setCurrency] = useState<string>(DEFAULT_CURRENCY);
  const [deleteOpen, setDeleteOpen] = useState(false);

  useEffect(() => {
    if (workspace) {
      setPick(workspaceCompanyPick(workspace.name, workspace.company));
      setRegion(workspace.defaultRegion);
      setCurrency(workspace.defaultCurrency);
    }
  }, [workspace]);

  const name = pick ? pickedCompanyName(pick).trim() : "";
  const pickedAnotherCompany =
    pick?.source === "universe" && pick.company.apolloAccountId !== workspace?.company?.apolloAccountId;

  const save = useMutation({
    mutationFn: () =>
      workspaceApi.updateWorkspace({
        name,
        apolloAccountId: pick?.source === "universe" ? pick.company.apolloAccountId : "",
        defaultRegion: region,
        defaultCurrency: currency,
      }),
    onSuccess: async () => {
      void queryClient.invalidateQueries({ queryKey: workspaceApi.WORKSPACE_KEY });
      // The name, logo mark and company logo also live in the auth summary the topbar reads.
      await reload();
      toast("Workspace settings saved");
    },
    onError: (error) => toast(messageFor(error)),
  });

  if (!workspace) return null;

  return (
    <>
      <PageHeader title="General" subtitle="Workspace identity and defaults" />

      <div className="rounded-[10px] border border-u-border bg-u-raised p-5">
        <div className="mb-5 flex items-center gap-3.5">
          {workspace.company?.logoUrl ? (
            <CompanyLogo name={workspace.name} logo={workspace.company.logoUrl} size={44} />
          ) : (
            <span className="grid size-11 place-items-center rounded-[11px] bg-u-accent-solid font-mono text-lg font-bold text-white">
              {workspace.logoMark ?? workspace.name[0]}
            </span>
          )}
          <div>
            <div className="text-sm font-semibold">{workspace.name}</div>
            {workspace.company && (
              <div className="mt-0.5 font-mono text-[11.5px] text-u-text3">
                {[workspace.company.industry, companyLocationOf(workspace.company)].filter(Boolean).join(" · ")}
              </div>
            )}
            <div className="mt-0.5 font-mono text-[11.5px] text-u-text3">
              {workspace.plan.toLowerCase()} plan · {workspace.memberCount}{" "}
              {workspace.memberCount === 1 ? "member" : "members"}
            </div>
          </div>
        </div>

        {pick && (
          <span className="mb-1.5 block font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-u-text3">
            Organization
          </span>
        )}
        <div className={pick ? undefined : "mb-4"}>
          <CompanyPicker label="Organization" pick={pick} onPick={setPick} asksCustomDetails={false} />
          {pickedAnotherCompany && (
            <p className="mt-1.5 font-mono text-[11.5px] text-u-text3">
              Saving fills the persona's sectors and geographies from this company.
            </p>
          )}
        </div>

        <div className="grid grid-cols-1 gap-x-4 gap-y-3.5 md:grid-cols-2">
          <Field label="Workspace URL">
            <div className="rounded-lg border border-u-border bg-u-surface px-3 py-[9px] font-mono text-[13px] font-medium text-u-text2">
              {window.location.host}/w/{workspace.slug}
            </div>
          </Field>
          <Field label="Default region">
            <Select value={region} onChange={(event) => setRegion(event.target.value)} className="!bg-u-surface">
              {REGIONS.map((r) => (
                <option key={r}>{r}</option>
              ))}
            </Select>
          </Field>
          <Field label="Default currency">
            <Select value={currency} onChange={(event) => setCurrency(event.target.value)} className="!bg-u-surface">
              {/* A code stored before the list grew stays offered: an unmatched select would post blank. */}
              {[...new Set([currency, ...CURRENCIES])].map((c) => (
                <option key={c}>{c}</option>
              ))}
            </Select>
          </Field>
        </div>

        <div className="mt-5 flex justify-end">
          <Button loading={save.isPending} disabled={!name} onClick={() => save.mutate()}>
            Save changes
          </Button>
        </div>
      </div>

      {/* Keyed on the firm too: a re-pick refiles the persona's sectors and country server-side. */}
      <WorkspacePersonaCard
        key={`${workspace.id}:${workspace.company?.apolloAccountId ?? ""}`}
        persona={workspace.persona}
      />

      <div className="mt-4 rounded-[10px] border border-u-offlimits bg-u-offlimits-tint p-5">
        <div className="flex items-center gap-3">
          <div className="flex-1">
            <div className="text-[13px] font-semibold text-u-offlimits">Delete workspace</div>
            <div className="mt-1 font-mono text-[11.5px] text-u-text3">
              Permanently removes all projects, candidates and client records. This cannot be undone.
            </div>
          </div>
          <Button
            variant="secondary"
            className="!border-u-offlimits !text-u-offlimits hover:!bg-u-offlimits hover:!text-white"
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
      <p className="mb-4 text-[13px] text-u-text2">
        This removes every member and cancels outstanding invitations. Type{" "}
        <b className="font-semibold text-u-text">{workspaceName}</b> to confirm.
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
          className="!border-u-offlimits !bg-u-offlimits !text-white hover:!brightness-105"
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

function companyLocationOf(company: WorkspaceCompany): string {
  return [company.city, company.country].filter(Boolean).join(", ");
}
