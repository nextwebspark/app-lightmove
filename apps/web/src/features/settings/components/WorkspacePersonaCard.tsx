import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Button, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import type { HiringPersona } from "../../workspace/api/types";
import { PersonaFields } from "../../workspace/components/PersonaFields";

/** Settings → General's firm persona: what the firm is, kept for the assistant to tailor research to. */
export function WorkspacePersonaCard({ persona }: { persona: HiringPersona }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [draft, setDraft] = useState<HiringPersona>(persona);

  const save = useMutation({
    mutationFn: () => workspaceApi.updatePersona(draft),
    onSuccess: (saved) => {
      queryClient.setQueryData(workspaceApi.WORKSPACE_KEY, saved);
      setDraft(saved.persona);
      toast("Firm persona saved");
    },
    onError: (error) => toast(messageFor(error)),
  });

  return (
    <div className="mt-4 rounded-[10px] border border-u-border bg-u-raised p-5">
      <div className="mb-4">
        <div className="text-sm font-semibold">Firm persona</div>
        <div className="mt-0.5 font-mono text-[11.5px] text-u-text3">
          What your firm does — Uncava's assistant uses it to tailor research to you.
        </div>
      </div>

      <PersonaFields
        value={draft}
        onChange={setDraft}
        idPrefix="firm-persona"
        summaryPlaceholder="e.g. Board and C-suite search for family groups and sovereign-backed companies in the Gulf"
        textAreaClassName="!bg-u-surface"
      />

      <div className="flex justify-end">
        <Button loading={save.isPending} onClick={() => save.mutate()}>
          Save persona
        </Button>
      </div>
    </div>
  );
}
