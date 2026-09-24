import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { Button, Field, TextArea, useToast } from "../../../components/ui";
import { TagListInput } from "../../../components/ui/TagListInput";
import { messageFor } from "../../../lib/errorCodes";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import type { WorkspacePersona } from "../../workspace/api/types";

/** Mirrors the `@Size` caps on UpdateWorkspacePersonaRequest. */
const MAX_LIST_ITEMS = 20;
const MAX_TEXT_LENGTH = 2000;

/** Settings → General's firm persona: what the firm is, kept for the assistant to tailor research to. */
export function WorkspacePersonaCard({ persona }: { persona: WorkspacePersona }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [draft, setDraft] = useState<WorkspacePersona>(persona);

  const save = useMutation({
    mutationFn: () => workspaceApi.updatePersona(draft),
    onSuccess: (saved) => {
      queryClient.setQueryData(workspaceApi.WORKSPACE_KEY, saved);
      setDraft(saved.persona);
      toast("Firm persona saved");
    },
    onError: (error) => toast(messageFor(error)),
  });

  const update = (patch: Partial<WorkspacePersona>) => setDraft((current) => ({ ...current, ...patch }));

  return (
    <div className="mt-4 rounded-[10px] border border-line-soft bg-panel2 p-5">
      <div className="mb-4">
        <div className="text-sm font-semibold">Firm persona</div>
        <div className="mt-0.5 font-mono text-[11.5px] text-text3">
          What your firm does — Uncava's assistant uses it to tailor research to you.
        </div>
      </div>

      <Field label="Main business">
        <TextArea
          rows={3}
          maxLength={MAX_TEXT_LENGTH}
          value={draft.summary ?? ""}
          onChange={(event) => update({ summary: event.target.value })}
          placeholder="e.g. Board and C-suite search for family groups and sovereign-backed companies in the Gulf"
          className="!bg-panel"
        />
      </Field>

      <div className="grid grid-cols-1 gap-x-4 md:grid-cols-2">
        <ListField label="Sectors">
          <TagListInput
            ariaLabel="Add a sector"
            values={draft.sectors}
            onChange={(sectors) => update({ sectors })}
            placeholder="Type a sector, press Enter"
            maxItems={MAX_LIST_ITEMS}
          />
        </ListField>
        <ListField label="Geographies">
          <TagListInput
            ariaLabel="Add a geography"
            values={draft.geographies}
            onChange={(geographies) => update({ geographies })}
            placeholder="e.g. GCC, Levant"
            maxItems={MAX_LIST_ITEMS}
          />
        </ListField>
      </div>

      <ListField label="Main competitors">
        <TagListInput
          ariaLabel="Add a competitor"
          values={draft.competitors}
          onChange={(competitors) => update({ competitors })}
          placeholder="Type a firm, press Enter"
          maxItems={MAX_LIST_ITEMS}
        />
      </ListField>

      <Field label="Anything else the assistant should know">
        <TextArea
          rows={2}
          maxLength={MAX_TEXT_LENGTH}
          value={draft.notes ?? ""}
          onChange={(event) => update({ notes: event.target.value })}
          className="!bg-panel"
        />
      </Field>

      <div className="flex justify-end">
        <Button loading={save.isPending} onClick={() => save.mutate()}>
          Save persona
        </Button>
      </div>
    </div>
  );
}

/**
 * `Field` is a `<label>`, which forwards a click to its first control — here a chip's remove button,
 * so clicking the caption would delete a chip. The chip lists take a plain caption instead.
 */
function ListField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="mb-4">
      <div className="mb-1.5 font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3">
        {label}
      </div>
      {children}
    </div>
  );
}
