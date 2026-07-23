import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useOutletContext } from "react-router-dom";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { Spinner, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { useAutosave } from "../../../lib/useAutosave";
import type { Project } from "../../projects/api/types";
import * as sourcingApi from "../../sourcing/api/sourcingApi";
import * as companiesApi from "../api/companiesApi";
import * as strategyApi from "../api/strategyApi";
import type { Chip, CompanyRef, CompanySearchResult, SectorGroup, Strategy } from "../api/types";
import { CompanyListPanel } from "../components/CompanyListPanel";
import { CompanySizePanel } from "../components/CompanySizePanel";
import { EstimateBanner } from "../components/EstimateBanner";
import { ScopeChipPanel } from "../components/ScopeChipPanel";
import { SectorPanel } from "../components/SectorPanel";
import { StrategyNav } from "../components/StrategyNav";
import { companyKeyOf } from "../lib/companyKey";
import { GEOGRAPHY_MARKETS } from "../lib/geographyMarkets";
import { capChips, mergeSuggestions, sameChips, withoutDirectDupes } from "../lib/mergeSuggestions";
import { OWNERSHIP_STRUCTURES } from "../lib/ownershipStructures";

/** How many chips each suggestion group holds at most; the DTO ceilings sit above these. */
const ADJACENT_CAP = 20;
const INFERRED_CAP = 15;

/** One scope autosave: the PUT to run, whether it changes the Sourcing scope, and any rollback. */
interface ScopeWrite {
  run: () => Promise<Strategy>;
  affectsSourcing?: boolean;
  onError?: () => void;
}

/** The Strategy tab: loads the sector scope, then hands the editor a snapshot to draft against. */
export function StrategyPage() {
  const { project } = useOutletContext<ProjectOutletContext>();
  const { data: strategy } = useQuery({
    queryKey: strategyApi.STRATEGY_KEY(project.id),
    queryFn: () => strategyApi.getStrategy(project.id),
  });

  if (!strategy) {
    return (
      <div className="flex justify-center pt-24">
        <Spinner />
      </div>
    );
  }

  return <StrategyEditor key={project.id} project={project} strategy={strategy} />;
}

/**
 * The sector-scope editor (Project.dc.html, Strategy page — the editable pre-lock state). There is no
 * Save button: every edit autosaves the whole scope as a snapshot PUT. Choosing direct sectors drives
 * a suggestions query whose results are merged into the Adjacent and Inferred groups, pre-selected but
 * freely deselectable; a live estimate counts the companies the current selection matches.
 */
function StrategyEditor({ project, strategy }: { project: Project; strategy: Strategy }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const navigate = useNavigate();
  const key = strategyApi.STRATEGY_KEY(project.id);

  const [draft, setDraft] = useState<Strategy>(strategy);
  const draftRef = useRef(draft);
  draftRef.current = draft;

  // Which scope section the panel shows. The sections switch in place (matching the mockup — one screen,
  // one shared universe), rather than being separate routes.
  const [activeKey, setActiveKey] = useState("sector");

  // Sourcing reads the saved scope through a selection-blind query key, so a scope write must mark
  // that list stale for it to refetch on the next visit. Fired only from the writes Sourcing's
  // ScopeFilter actually reads — sectors, size, geography, and the company lists, but never ownership:
  // ownership now maps onto app_lm_companies.org_type, but wiring that join into Sourcing is a
  // separate session, so for now the selection is tracked without narrowing the list.
  const invalidateSourcing = () =>
    void queryClient.invalidateQueries({ queryKey: sourcingApi.SOURCING_KEY_PREFIX(project.id) });

  // A single tracked mutation stands behind every scope autosave. Its shared key lets Sourcing detect an
  // in-flight save via useIsMutating and hold its own read until this commits — closing the window where a
  // just-navigated Sourcing would otherwise show the pre-edit list. The side effects live in mutationFn
  // (not onSuccess) so they still run when the autosave flushes on unmount, i.e. mid-navigation.
  const strategyWrite = useMutation({
    mutationKey: strategyApi.STRATEGY_WRITE_KEY(project.id),
    mutationFn: async (write: ScopeWrite) => {
      try {
        queryClient.setQueryData(key, await write.run());
        if (write.affectsSourcing) invalidateSourcing();
      } catch (error) {
        write.onError?.();
        toast(messageFor(error));
        throw error;
      }
    },
  });

  const persist =
    (call: (payload: Strategy) => Promise<Strategy>, options?: Omit<ScopeWrite, "run">) =>
    (payload: Strategy) =>
      strategyWrite.mutateAsync({ run: () => call(payload), ...options });

  const autosave = useAutosave(
    persist((payload) => strategyApi.putSectors(project.id, payload), { affectsSourcing: true }),
  );

  const change = (next: Strategy) => {
    setDraft(next);
    autosave.schedule(next);
  };

  // Company Size saves its own snapshot on its own endpoint, independent of the sector autosave.
  const sizeAutosave = useAutosave(
    persist((payload) => strategyApi.putCompanySize(project.id, payload.employee, payload.revenue), {
      affectsSourcing: true,
    }),
  );

  const toggleBand = (axis: "employee" | "revenue", value: string) => {
    const current = draftRef.current[axis];
    const next = current.includes(value)
      ? current.filter((v) => v !== value)
      : [...current, value];
    const nextDraft = { ...draftRef.current, [axis]: next };
    setDraft(nextDraft);
    sizeAutosave.schedule(nextDraft);
  };

  // Ownership and Location each save their own snapshot on their own endpoint, like Company Size.
  // Ownership is not part of the Sourcing scope, so it alone does not invalidate that list.
  const ownershipAutosave = useAutosave(
    persist((payload) => strategyApi.putOwnership(project.id, payload.structures)),
  );
  const geographyAutosave = useAutosave(
    persist((payload) => strategyApi.putGeography(project.id, payload.markets), { affectsSourcing: true }),
  );

  const toggleCatalogValue = (
    field: "markets" | "structures",
    value: string,
    save: { schedule: (payload: Strategy) => void },
  ) => {
    const current = draftRef.current[field];
    const next = current.includes(value)
      ? current.filter((v) => v !== value)
      : [...current, value];
    const nextDraft = { ...draftRef.current, [field]: next };
    setDraft(nextDraft);
    save.schedule(nextDraft);
  };

  const toggleStructure = (value: string) => toggleCatalogValue("structures", value, ownershipAutosave);
  const toggleMarket = (value: string) => toggleCatalogValue("markets", value, geographyAutosave);

  // The company lists each save their own snapshot too — the wire carries bare (source, sourceId)
  // keys and the server resolves the display snapshot, so the PUT strips the refs down to keys.
  // A rejected save rolls the list back to the last server-acknowledged state: leaving the bad ref
  // in the draft would wedge every subsequent autosave of that list on the same 400.
  const persistCompanyList = (
    field: "targets" | "offLimits",
    putList: (projectId: string, companies: strategyApi.CompanyKey[]) => Promise<Strategy>,
  ) =>
    persist((payload) => putList(project.id, keysOf(payload[field])), {
      affectsSourcing: true,
      onError: () => {
        const server = queryClient.getQueryData<Strategy>(key);
        setDraft((current) => ({ ...current, [field]: server?.[field] ?? [] }));
      },
    });
  const targetsAutosave = useAutosave(persistCompanyList("targets", strategyApi.putTargets));
  const offLimitsAutosave = useAutosave(persistCompanyList("offLimits", strategyApi.putOffLimits));

  const addCompany = (
    field: "targets" | "offLimits",
    company: CompanySearchResult,
    save: { schedule: (payload: Strategy) => void },
  ) => {
    const current = draftRef.current;
    const pickedKey = companyKeyOf(company);
    if (current[field].some((ref) => companyKeyOf(ref) === pickedKey)) return;
    // Unreachable via the UI (the combobox excludes both lists' keys); kept as the client-side
    // mirror of the server's cross-list guard.
    const otherField = field === "targets" ? "offLimits" : "targets";
    if (current[otherField].some((ref) => companyKeyOf(ref) === pickedKey)) {
      toast(
        field === "targets"
          ? `${company.name} is already off-limits`
          : `${company.name} is already on the target list`,
      );
      return;
    }
    const ref: CompanyRef = {
      source: company.source,
      sourceId: company.sourceId,
      name: company.name,
      domain: company.domain,
      slogan: company.slogan,
      logo: company.logo,
      hqCity: company.hqCity,
      hqCountry: company.hqCountry,
    };
    const nextDraft = { ...current, [field]: [...current[field], ref] };
    setDraft(nextDraft);
    save.schedule(nextDraft);
  };

  const removeCompany = (
    field: "targets" | "offLimits",
    company: CompanyRef,
    save: { schedule: (payload: Strategy) => void },
  ) => {
    const current = draftRef.current;
    const removedKey = companyKeyOf(company);
    const nextDraft = {
      ...current,
      [field]: current[field].filter((ref) => companyKeyOf(ref) !== removedKey),
    };
    setDraft(nextDraft);
    save.schedule(nextDraft);
  };

  // Both lists exclude both lists' keys from the search: the same-list half hides what is already
  // added, the cross-list half stops a contradictory pick before the toast would.
  const listedKeys = useMemo(
    () => new Set([...draft.targets, ...draft.offLimits].map(companyKeyOf)),
    [draft.targets, draft.offLimits],
  );

  // The company universe for the typeahead — fetched once, never restale (it changes only on an ETL sync).
  const { data: sectorList } = useQuery({
    queryKey: companiesApi.SECTORS_KEY,
    queryFn: companiesApi.getSectors,
    staleTime: Infinity,
  });

  // Suggestions recompute from the *selected* direct labels only; deselecting a direct chip narrows them.
  const selectedDirect = useMemo(() => labelsOf(selectedChips(draft.direct)), [draft.direct]);
  const suggestionsQuery = useQuery({
    queryKey: companiesApi.SUGGESTIONS_KEY(selectedDirect),
    queryFn: () => companiesApi.getSuggestions(selectedDirect),
    enabled: selectedDirect.length > 0,
  });
  const suggestions = suggestionsQuery.data;

  // Reconcile the suggestion groups with the current direct sectors. Suggestions are derived: when the
  // selected-direct set changes the groups recompute, dropping anything the remaining directs no longer
  // suggest (so deselecting a direct also clears its adjacent/inferred), and clearing entirely once no
  // direct is selected. Reads the latest draft through a ref — depending on the chips would loop — and
  // keys on the direct set so a shrink re-runs even before the refetch lands. A direct sector never
  // doubles as a suggestion, and each group is capped so the stored list can't outgrow the DTO ceiling.
  const selectedDirectKey = selectedDirect.join("\u0000");
  useEffect(() => {
    const current = draftRef.current;
    if (selectedDirect.length === 0) {
      if (current.adjacent.length > 0 || current.inferred.length > 0) {
        change({ ...current, adjacent: [], inferred: [] });
      }
      return;
    }
    if (!suggestions) return;
    const directLabels = labelsOf(current.direct);
    const adjacent = capChips(
      withoutDirectDupes(mergeSuggestions(current.adjacent, suggestions.adjacent), directLabels),
      ADJACENT_CAP,
    );
    const inferred = capChips(
      withoutDirectDupes(
        mergeSuggestions(current.inferred, suggestions.inferredTags.map((tag) => tag.tag)),
        directLabels,
      ),
      INFERRED_CAP,
    );
    if (sameChips(adjacent, current.adjacent) && sameChips(inferred, current.inferred)) return;
    change({ ...current, adjacent, inferred });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [suggestions, selectedDirectKey]);

  const selectedSectors = useMemo(
    () => [...labelsOf(selectedChips(draft.direct)), ...labelsOf(selectedChips(draft.adjacent))],
    [draft.direct, draft.adjacent],
  );
  const selectedTags = useMemo(() => labelsOf(selectedChips(draft.inferred)), [draft.inferred]);
  const hasScope = selectedSectors.length > 0 || selectedTags.length > 0;

  const estimate = useQuery({
    queryKey: companiesApi.ESTIMATE_KEY(
      selectedSectors,
      selectedTags,
      draft.employee,
      draft.revenue,
      draft.markets,
    ),
    queryFn: () =>
      companiesApi.getEstimate(selectedSectors, selectedTags, draft.employee, draft.revenue, draft.markets),
    enabled: hasScope,
    placeholderData: keepPreviousData,
  });

  const toggle = (kind: SectorGroup, label: string) => {
    change({
      ...draft,
      [kind]: draft[kind].map((chip) =>
        chip.label === label ? { ...chip, selected: !chip.selected } : chip,
      ),
    });
  };

  const addDirect = (label: string) => {
    if (draft.direct.some((chip) => chip.label.toLowerCase() === label.toLowerCase())) return;
    // A new direct sector supersedes any adjacent/inferred chip of the same name — strip it there now,
    // rather than waiting for the next suggestions fetch to exclude it.
    change({
      ...draft,
      direct: [...draft.direct, { label, selected: true }],
      adjacent: withoutDirectDupes(draft.adjacent, [label]),
      inferred: withoutDirectDupes(draft.inferred, [label]),
    });
  };

  const selectedCount =
    selectedChips(draft.direct).length +
    selectedChips(draft.adjacent).length +
    selectedChips(draft.inferred).length;

  return (
    <div className="animate-fade-up">
      <div className="mb-5">
        <div className="font-sans text-[19px] font-semibold leading-[1.2]">Strategy</div>
        <p className="mt-1 max-w-[640px] text-[13px] text-text2">
          Define the company universe Sourcing searches. Pick your core sectors — the system suggests
          adjacent sectors and related tags to widen the pool.
        </p>
      </div>

      {/* Hold the count until suggestions have settled and merged, so it lands once rather than
          jumping from the direct-only total to the full one. */}
      <EstimateBanner
        count={hasScope ? estimate.data?.count : 0}
        loading={hasScope && (estimate.isFetching || suggestionsQuery.isFetching)}
        onGoToSourcing={() => navigate(`/projects/${project.id}/sourcing`)}
      />

      <div className="grid grid-cols-[250px_1fr] items-start gap-4">
        <StrategyNav
          activeKey={activeKey}
          counts={{
            sector: selectedCount,
            size: draft.employee.length + draft.revenue.length,
            ownership: draft.structures.length,
            location: draft.markets.length,
            seed: draft.targets.length,
            offlimits: draft.offLimits.length,
          }}
          onSelect={setActiveKey}
        />
        {/* NOTE: the estimate above is wired to sector, company-size, and geography now. Ownership Type
            still doesn't narrow it — the enum now maps onto app_lm_companies.org_type, but wiring that
            join into the estimate and Sourcing is a separate session, so for now it's tracked on the
            strategy and deliberately left out of both. */}
        {activeKey === "size" ? (
          <CompanySizePanel strategy={draft} onToggle={toggleBand} />
        ) : activeKey === "ownership" ? (
          <ScopeChipPanel
            title="Ownership Type"
            subtitle="Narrows by organization type, not sector"
            groupLabel="Structures"
            options={OWNERSHIP_STRUCTURES}
            selected={draft.structures}
            onToggle={toggleStructure}
          />
        ) : activeKey === "location" ? (
          <ScopeChipPanel
            title="Location"
            subtitle="Headquarters or major operating base in region"
            groupLabel="Markets"
            options={GEOGRAPHY_MARKETS}
            selected={draft.markets}
            onToggle={toggleMarket}
          />
        ) : activeKey === "seed" ? (
          <CompanyListPanel
            panelKey="seed"
            title="Target List Seeding"
            subtitle="Manually added companies — included in results directly, bypassing Required filters"
            companies={draft.targets}
            excludedKeys={listedKeys}
            browseSectors={selectedDirect}
            browseOrder="revenue_desc"
            onAdd={(company) => addCompany("targets", company, targetsAutosave)}
            onRemove={(company) => removeCompany("targets", company, targetsAutosave)}
          />
        ) : activeKey === "offlimits" ? (
          <CompanyListPanel
            panelKey="offlimits"
            title="Off-limits"
            subtitle="Excluded from sourcing — inherited from the client's entity record, plus project additions"
            companies={draft.offLimits}
            excludedKeys={listedKeys}
            browseSectors={selectedDirect}
            browseOrder="revenue_asc"
            accent="red"
            onAdd={(company) => addCompany("offLimits", company, offLimitsAutosave)}
            onRemove={(company) => removeCompany("offLimits", company, offLimitsAutosave)}
          />
        ) : (
          <SectorPanel
            strategy={draft}
            sectors={sectorList?.sectors ?? []}
            onToggle={toggle}
            onAddDirect={addDirect}
          />
        )}
      </div>
    </div>
  );
}

function selectedChips(chips: Chip[]): Chip[] {
  return chips.filter((chip) => chip.selected);
}

function keysOf(refs: CompanyRef[]): strategyApi.CompanyKey[] {
  return refs.map((ref) => ({ source: ref.source, sourceId: ref.sourceId }));
}

function labelsOf(chips: Chip[]): string[] {
  return chips.map((chip) => chip.label);
}
