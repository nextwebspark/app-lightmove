import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState, type ReactNode } from "react";
import { Button, Field, TextArea, useToast } from "../../../components/ui";
import type { ComboboxOption } from "../../../components/ui/FacetCombobox";
import { TagListInput } from "../../../components/ui/TagListInput";
import { useCountries } from "../../../lib/countries";
import { messageFor } from "../../../lib/errorCodes";
import { industryDisplayName } from "../../../lib/format";
import * as companiesApi from "../../strategy/api/companiesApi";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import type { WorkspacePersona } from "../../workspace/api/types";

/** Mirrors the `@Size` caps on UpdateWorkspacePersonaRequest. */
const MAX_LIST_ITEMS = 20;
const MAX_TEXT_LENGTH = 2000;
const FACETS_STALE_MS = 10 * 60 * 1000;
const REGIONS = ["GCC", "MENA", "Levant", "Europe", "Global"];

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

  const sectorOptions = useSectorOptions();
  const geographyOptions = useGeographyOptions();

  const update = (patch: Partial<WorkspacePersona>) => setDraft((current) => ({ ...current, ...patch }));

  return (
    <div className="mt-4 rounded-[10px] border border-u-border bg-u-raised p-5">
      <div className="mb-4">
        <div className="text-sm font-semibold">Firm persona</div>
        <div className="mt-0.5 font-mono text-[11.5px] text-u-text3">
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
          className="!bg-u-surface"
        />
      </Field>

      <div className="grid grid-cols-1 gap-x-4 md:grid-cols-2">
        <ListField label="Sectors">
          <TagListInput
            ariaLabel="Add a sector"
            values={draft.sectors}
            onChange={(sectors) => update({ sectors })}
            options={sectorOptions}
            listId="persona-sector-suggestions"
            placeholder="Pick or type a sector"
            maxItems={MAX_LIST_ITEMS}
          />
        </ListField>
        <ListField label="Geographies">
          <TagListInput
            ariaLabel="Add a geography"
            values={draft.geographies}
            onChange={(geographies) => update({ geographies })}
            options={geographyOptions}
            listId="persona-geography-suggestions"
            placeholder="Pick a country, or type GCC, Levant…"
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
          className="!bg-u-surface"
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

/** The universe's industries and the sectors they group under; undefined while refused, so the chips stay free text. */
function useSectorOptions(): ComboboxOption[] | undefined {
  const facets = useQuery({
    queryKey: companiesApi.FACETS_KEY,
    queryFn: companiesApi.getFacets,
    staleTime: FACETS_STALE_MS,
  });
  const groups = facets.data?.sectorGroups;
  return useMemo(() => {
    if (!groups) return undefined;
    const labels = [
      ...groups.flatMap((group) => group.industries.map((industry) => industryDisplayName(industry.label))),
      ...groups.map((group) => group.name),
    ];
    return [...new Set(labels)]
      .sort((first, second) => first.localeCompare(second))
      .map((label) => ({ value: label, label }));
  }, [groups]);
}

/** Every country the catalog names, after the regions a firm is as often described by. */
function useGeographyOptions(): ComboboxOption[] | undefined {
  const { options, isError } = useCountries();
  return useMemo(
    () => (isError ? undefined : [...REGIONS.map((region) => ({ value: region, label: region })), ...options]),
    [options, isError],
  );
}

/**
 * `Field` is a `<label>`, which forwards a click to its first control — here a chip's remove button,
 * so clicking the caption would delete a chip. The chip lists take a plain caption instead.
 */
function ListField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="mb-4">
      <div className="mb-1.5 font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-u-text3">
        {label}
      </div>
      {children}
    </div>
  );
}
