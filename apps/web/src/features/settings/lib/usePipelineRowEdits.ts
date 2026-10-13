import { useState } from "react";
import { useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import type { PipelineCatalog } from "../../workspace/api/types";
import { usePipelineWrite } from "../../workspace/lib/usePipelineCatalog";

export interface RenameDraft {
  renaming: boolean;
  draft: string;
  setDraft: (value: string) => void;
  begin: () => void;
  end: () => void;
}

/** A row's name while it is being retyped; `begin` starts from the name as it stands. */
export function useRenameDraft(label: string): RenameDraft {
  const [renaming, setRenaming] = useState(false);
  const [draft, setDraft] = useState(label);
  return {
    renaming,
    draft,
    setDraft,
    begin: () => {
      setDraft(label);
      setRenaming(true);
    },
    end: () => setRenaming(false),
  };
}

/** A stage's or a status's changes: a saved rename closes its form, a refusal says why. */
export function usePipelineRowChange<TChange extends { label?: string }>(
  write: (change: TChange) => Promise<PipelineCatalog>,
  rename: RenameDraft,
) {
  const toast = useToast();
  const mutation = usePipelineWrite(write);
  const change = (patch: TChange, success?: string) =>
    mutation.mutate(patch, {
      onSuccess: () => {
        if (patch.label !== undefined) rename.end();
        if (success) toast(success);
      },
      onError: (error) => toast.error(messageFor(error)),
    });
  return { change, isPending: mutation.isPending };
}
