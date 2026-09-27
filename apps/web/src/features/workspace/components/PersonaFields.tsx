import { useQuery } from "@tanstack/react-query";
import { useMemo, type ReactNode } from "react";
import { Field, TextArea } from "../../../components/ui";
import type { ComboboxOption } from "../../../components/ui/FacetCombobox";
import { TagListInput } from "../../../components/ui/TagListInput";
import { useCountries } from "../../../lib/countries";
import { industryDisplayName } from "../../../lib/format";
import * as companiesApi from "../../strategy/api/companiesApi";
import type { HiringPersona } from "../api/types";

/** Mirrors the `@Size` caps on both persona update requests. */
const MAX_LIST_ITEMS = 20;
const MAX_TEXT_LENGTH = 2000;
const FACETS_STALE_MS = 10 * 60 * 1000;
const REGIONS = ["GCC", "MENA", "Levant", "Europe", "Global"];

/**
 * A hiring company's persona as editable fields — the firm's own in Settings, an agency client's in its
 * drawer. One editor for both, so the two cannot drift apart.
 */
export function PersonaFields({
  value,
  onChange,
  idPrefix,
  summaryPlaceholder,
  textAreaClassName,
}: {
  value: HiringPersona;
  onChange: (value: HiringPersona) => void;
  /** Keeps the two suggestion lists' ids unique on a page. */
  idPrefix: string;
  summaryPlaceholder: string;
  /** The text boxes sit on a raised card in Settings and on the drawer's surface; each needs contrast. */
  textAreaClassName?: string;
}) {
  const sectorOptions = useSectorOptions();
  const geographyOptions = useGeographyOptions();

  const update = (patch: Partial<HiringPersona>) => onChange({ ...value, ...patch });

  return (
    <>
      <Field label="Main business">
        <TextArea
          rows={3}
          maxLength={MAX_TEXT_LENGTH}
          value={value.summary ?? ""}
          onChange={(event) => update({ summary: event.target.value })}
          placeholder={summaryPlaceholder}
          className={textAreaClassName}
        />
      </Field>

      <div className="grid grid-cols-1 gap-x-4 md:grid-cols-2">
        <ListField label="Sectors">
          <TagListInput
            ariaLabel="Add a sector"
            values={value.sectors}
            onChange={(sectors) => update({ sectors })}
            options={sectorOptions}
            listId={`${idPrefix}-sector-suggestions`}
            placeholder="Pick or type a sector"
            maxItems={MAX_LIST_ITEMS}
          />
        </ListField>
        <ListField label="Geographies">
          <TagListInput
            ariaLabel="Add a geography"
            values={value.geographies}
            onChange={(geographies) => update({ geographies })}
            options={geographyOptions}
            listId={`${idPrefix}-geography-suggestions`}
            placeholder="Pick a country, or type GCC, Levant…"
            maxItems={MAX_LIST_ITEMS}
          />
        </ListField>
      </div>

      <ListField label="Main competitors">
        <TagListInput
          ariaLabel="Add a competitor"
          values={value.competitors}
          onChange={(competitors) => update({ competitors })}
          placeholder="Type a firm, press Enter"
          maxItems={MAX_LIST_ITEMS}
        />
      </ListField>

      <Field label="Anything else the assistant should know">
        <TextArea
          rows={2}
          maxLength={MAX_TEXT_LENGTH}
          value={value.notes ?? ""}
          onChange={(event) => update({ notes: event.target.value })}
          className={textAreaClassName}
        />
      </Field>
    </>
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
