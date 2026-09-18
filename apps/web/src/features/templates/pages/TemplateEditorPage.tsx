import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState, type ReactNode } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Field, FormError, Input, Modal, Select, TextArea, useToast } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { cn } from "../../../lib/cn";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import { formatInstantDate } from "../../../lib/format";
import {
  noticePairLabel,
  noticePeriodOfPair,
  NOTICE_PERIODS,
  pairOfNoticePeriod,
} from "../../../lib/noticePeriod";
import { SENIORITY_LABELS, SENIORITY_TIERS } from "../../../lib/seniority";
import { POSITION_TEMPLATES_KEY } from "../../position/api/positionApi";
import type {
  BaseSalaryMode,
  BonusBasis,
  EmploymentType,
  IncentiveType,
  PositionDiscipline,
  PositionSeniority,
} from "../../position/api/types";
import { CompetencyPanel } from "../../position/components/CompetencyPanel";
import { CriteriaCard } from "../../position/components/CriteriaCard";
import { SegmentedControl } from "../../position/components/fields";
import { moveRow, toggle } from "../../position/lib/competencyRows";
import {
  BASE_SALARY_MODE_LABELS,
  BONUS_BASIS_LABELS,
  CURRENCIES,
  EMPLOYMENT_TYPE_LABELS,
  INCENTIVE_TYPE_LABELS,
} from "../../position/lib/labels";
import * as templateApi from "../api/templateAdminApi";
import type { TemplateDetail, TemplateScope } from "../api/types";
import { BenefitLines } from "../components/BenefitLines";
import { ChipListField } from "../components/ChipListField";
import { TemplateBadge } from "../components/TemplateBadge";
import { DISCIPLINE_LABELS, DISCIPLINES, SCOPE_COPY } from "../lib/labels";
import { blankDraft, draftOf, draftProblems, requestOf, type TemplateDraft } from "../lib/templateDraft";

type Lifecycle = "archive" | "restore" | "hide" | "show" | "reset" | "delete";

const LIFECYCLE: Record<Lifecycle, { label: string; destructive: boolean; confirm: string | null }> = {
  archive: { label: "Archive template", destructive: true, confirm: null },
  restore: { label: "Restore template", destructive: false, confirm: null },
  hide: { label: "Hide from my firm", destructive: false, confirm: null },
  show: { label: "Show to my firm", destructive: false, confirm: null },
  reset: {
    label: "Reset to library",
    destructive: true,
    confirm: "Your firm's copy is deleted and the LightMove library version takes its place. Mandates already drafted keep what they have.",
  },
  delete: {
    label: "Delete template",
    destructive: true,
    confirm: "The template is deleted for your firm. Mandates already drafted from it keep what they have.",
  },
};

const BANNER_TONES = {
  sky: "border-line-soft bg-sky-dim",
  amber: "border-amber-btn bg-amber-dim",
  plain: "border-line bg-panel2",
} as const;

const DISCARD = "Discard your unsaved changes?";

/** One template, opened from either Templates list — or a new one, at `…/new`. */
export function TemplateEditorPage({ scope }: { scope: TemplateScope }) {
  const { code } = useParams();
  // Keyed, so arriving at a created template's own URL starts a fresh editor instead of carrying the
  // new-template state across.
  return <TemplateEditor key={code ?? "new"} scope={scope} code={code ?? null} />;
}

function TemplateEditor({ scope, code }: { scope: TemplateScope; code: string | null }) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const toast = useToast();
  const { heading, path } = SCOPE_COPY[scope];

  const detailQuery = useQuery({
    queryKey: templateApi.TEMPLATE_DETAIL_KEY(scope, code ?? ""),
    queryFn: ({ signal }) => templateApi.getTemplate(scope, code!, signal),
    enabled: code !== null,
  });
  const detail = detailQuery.data ?? null;

  const [draft, setDraft] = useState<TemplateDraft | null>(code === null ? blankDraft() : null);
  const [dirty, setDirty] = useState(false);
  const [lockedTechnical, setLockedTechnical] = useState<ReadonlySet<string>>(new Set());
  const [lockedBehavioural, setLockedBehavioural] = useState<ReadonlySet<string>>(new Set());
  const [failure, setFailure] = useState<unknown>(null);
  const [confirming, setConfirming] = useState<Lifecycle | null>(null);

  // Seeded once from the server copy: a background refetch must never overwrite an edit in progress.
  useEffect(() => {
    if (detail && draft === null) setDraft(draftOf(detail));
  }, [detail, draft]);

  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  const update = (changes: Partial<TemplateDraft>) => {
    setDraft((current) => (current ? { ...current, ...changes } : current));
    setDirty(true);
  };

  const refreshLists = () => {
    void queryClient.invalidateQueries({ queryKey: templateApi.TEMPLATE_ADMIN_KEY(scope) });
    void queryClient.invalidateQueries({ queryKey: POSITION_TEMPLATES_KEY });
  };

  const adopt = (saved: TemplateDetail) => {
    queryClient.setQueryData(templateApi.TEMPLATE_DETAIL_KEY(scope, saved.code), saved);
    setDraft(draftOf(saved));
    setLockedTechnical(new Set());
    setLockedBehavioural(new Set());
    setDirty(false);
    refreshLists();
  };

  const save = useMutation({
    mutationFn: (current: TemplateDraft) =>
      code === null
        ? templateApi.createTemplate(scope, requestOf(current, null))
        : templateApi.saveTemplate(scope, code, requestOf(current, detail?.version ?? null)),
    onMutate: () => setFailure(null),
    onSuccess: (saved) => {
      adopt(saved);
      toast(savedMessage(scope, detail));
      if (code === null) navigate(`${path}/${encodeURIComponent(saved.code)}`, { replace: true });
    },
    onError: setFailure,
  });

  const lifecycle = useMutation({
    mutationFn: async (action: Lifecycle): Promise<TemplateDetail | null> => {
      if (code === null || !detail) return null;
      switch (action) {
        case "archive":
        case "restore":
          return templateApi.setTemplateActive(code, action === "restore");
        case "hide":
        case "show":
          return templateApi.setTemplateHidden(code, action === "hide");
        case "reset":
          await templateApi.removeTemplate(code, detail.version);
          return templateApi.getTemplate(scope, code);
        case "delete":
          await templateApi.removeTemplate(code, detail.version);
          return null;
      }
    },
    onSuccess: (result, action) => {
      setConfirming(null);
      refreshLists();
      if (action === "delete") {
        toast("Template deleted");
        navigate(path);
        return;
      }
      if (!result) return;
      queryClient.setQueryData(templateApi.TEMPLATE_DETAIL_KEY(scope, result.code), result);
      // A reset replaces the content itself; the other four change only whether it is offered.
      if (action === "reset") adopt(result);
      toast(lifecycleMessage(action));
    },
    onError: (error) => {
      setConfirming(null);
      toast(messageFor(error));
    },
  });

  const reloadAfterConflict = async () => {
    const fresh = await detailQuery.refetch();
    if (fresh.data) adopt(fresh.data);
    setFailure(null);
  };

  const handleLifecycle = (action: Lifecycle) => {
    if (LIFECYCLE[action].confirm) setConfirming(action);
    else lifecycle.mutate(action);
  };

  if (code !== null && detailQuery.isError) {
    return (
      <>
        <BackLink path={path} heading={heading} dirty={false} />
        <p role="alert" className="mt-4 rounded-lg bg-red-dim px-3 py-2.5 font-mono text-xs text-red">
          {messageFor(detailQuery.error)}
        </p>
      </>
    );
  }

  if (!draft || (code !== null && !detail)) {
    return <p className="font-mono text-xs text-text3">Loading template…</p>;
  }

  const noticePeriod = noticePeriodOfPair(draft.noticeValue, draft.noticeUnit);
  // A template written outside the app, or before step three offered five periods, keeps saying what
  // it says: the exchange schema is deliberately wider than this picker.
  const noticeAsRecorded =
    !noticePeriod && draft.noticeValue != null && draft.noticeUnit != null
      ? noticePairLabel(draft.noticeValue, draft.noticeUnit)
      : null;

  const problems = draftProblems(draft);
  const lifecycleAction = code === null || !detail ? null : lifecycleOf(scope, detail);
  const banner = bannerOf(scope, detail);
  const saveLabel = saveLabelOf(scope, detail);
  const canSave = dirty && problems.length === 0 && !save.isPending;
  const stale = codeOf(failure) === "TEMPLATE_STALE";
  const serverProblems =
    failure instanceof ApiRequestError ? Object.values(failure.fieldErrors) : [];

  const saveButton = (
    <Button loading={save.isPending} disabled={!canSave} onClick={() => save.mutate(draft)}>
      {saveLabel}
    </Button>
  );

  return (
    <>
      <BackLink path={path} heading={heading} dirty={dirty} />

      <div className="mb-3.5 mt-2.5 flex flex-wrap items-start gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-[19px] font-semibold leading-tight">{draft.title.trim() || "Untitled template"}</h1>
            {detail && <TemplateBadge scope={scope} template={detail} />}
          </div>
          <div className="mt-1 font-mono text-xs text-text3">{metaLineOf(scope, detail)}</div>
        </div>
        <div className="ms-auto flex flex-none items-center gap-2.5">
          {dirty && <span className="font-mono text-[11.5px] font-medium text-amber">Unsaved changes</span>}
          {saveButton}
        </div>
      </div>

      <div
        className={cn(
          "mb-[18px] flex flex-wrap items-start gap-2.5 rounded-[10px] border px-3.5 py-3 font-mono text-xs leading-relaxed text-text2",
          BANNER_TONES[banner.tone],
        )}
      >
        <span className="min-w-[220px] flex-1">{banner.text}</span>
        {detail?.origin === "CUSTOMISED" && scope === "workspace" && (
          <Button variant="secondary" className="flex-none py-1.5 text-xs" onClick={() => handleLifecycle("reset")}>
            Reset to library
          </Button>
        )}
      </div>

      {stale ? (
        <div role="alert" className="mb-4 flex flex-wrap items-center gap-3 rounded-lg bg-red-dim px-3 py-2.5 font-mono text-[11.5px] text-red">
          <span className="flex-1">{messageFor(failure)}</span>
          <Button variant="secondary" className="py-1.5 text-xs" onClick={() => void reloadAfterConflict()}>
            Reload
          </Button>
        </div>
      ) : (
        <FormError message={failure ? serverProblems.join(" ") || messageFor(failure) : null} />
      )}

      <SectionCard title="Identity & matching" aside="How the picker lists this template, and which role titles are drafted from it">
        <Field label="Title">
          <Input value={draft.title} maxLength={160} onChange={(e) => update({ title: e.target.value })} className="!bg-panel" />
        </Field>
        <div className="grid grid-cols-1 gap-x-4 md:grid-cols-2">
          <Field label="Discipline">
            <Select
              value={draft.discipline}
              onChange={(e) => update({ discipline: e.target.value as PositionDiscipline })}
              className="!bg-panel"
            >
              {DISCIPLINES.map((discipline) => (
                <option key={discipline} value={discipline}>
                  {DISCIPLINE_LABELS[discipline]}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Seniority">
            <Select
              value={draft.seniority}
              onChange={(e) => update({ seniority: e.target.value as PositionSeniority })}
              className="!bg-panel"
            >
              {SENIORITY_TIERS.map((tier) => (
                <option key={tier} value={tier}>
                  {SENIORITY_LABELS[tier]}
                </option>
              ))}
            </Select>
          </Field>
        </div>
        <Field label="Summary — one line under the title in the picker">
          <Input value={draft.summary} maxLength={300} onChange={(e) => update({ summary: e.target.value })} className="!bg-panel" />
        </Field>
        <ChipListField
          label="Match keywords"
          values={draft.keywords}
          placeholder="Add a keyword, e.g. group cfo"
          hint="A new mandate whose role title contains any of these is drafted from this template. Case doesn't matter."
          max={20}
          maxLength={80}
          lowercase
          onChange={(keywords) => update({ keywords })}
        />
      </SectionCard>

      <SectionCard title="Position details" aside="Step 1 of the brief. Role title and location stay the mandate's own">
        <div className="grid grid-cols-1 gap-x-4 md:grid-cols-2">
          <Field label="Department">
            <Input value={draft.department} maxLength={160} onChange={(e) => update({ department: e.target.value })} className="!bg-panel" />
          </Field>
          <Field label="Employment type">
            <Select
              value={draft.employmentType ?? ""}
              onChange={(e) => update({ employmentType: (e.target.value || null) as EmploymentType | null })}
              className="!bg-panel"
            >
              <option value="">Not set</option>
              {Object.entries(EMPLOYMENT_TYPE_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </Field>
        </div>
        <ChipListField
          label="Key responsibilities"
          values={draft.responsibilities}
          placeholder="Add a responsibility…"
          max={20}
          maxLength={200}
          onChange={(responsibilities) => update({ responsibilities })}
        />
        <Field label="Ideal profile">
          <TextArea
            rows={5}
            value={draft.narrative}
            maxLength={4000}
            onChange={(e) => update({ narrative: e.target.value })}
            className="!bg-panel font-sans leading-relaxed"
          />
        </Field>
      </SectionCard>

      <SectionCard
        title="Mandate context"
        aside="The strategic-priority palette step 2 offers. Reason for hire and business driver stay per mandate"
      >
        <ChipListField
          label="Strategic priorities"
          values={draft.strategicPriorities}
          placeholder="Add a priority…"
          max={20}
          maxLength={120}
          onChange={(strategicPriorities) => update({ strategicPriorities })}
        />
      </SectionCard>

      <SectionCard title="Reporting structure" aside="Seeds step 3's org chart: the seat above the role, and the seats beneath it">
        <div className="grid grid-cols-1 gap-x-4 md:grid-cols-2">
          <Field label="Reports to">
            <Input value={draft.reportsTo} maxLength={160} onChange={(e) => update({ reportsTo: e.target.value })} className="!bg-panel" />
          </Field>
          <Field label="Notice period">
            <Select
              value={noticePeriod ?? noticeAsRecorded ?? ""}
              aria-label="Notice period"
              onChange={(e) =>
                update(
                  e.target.value === noticeAsRecorded
                    ? {}
                    : (pairOfNoticePeriod(e.target.value) ?? { noticeValue: null, noticeUnit: null }),
                )
              }
              className="!bg-panel"
            >
              <option value="">Not set</option>
              {NOTICE_PERIODS.map((period) => (
                <option key={period.label} value={period.label}>
                  {period.label}
                </option>
              ))}
              {noticeAsRecorded && (
                <option value={noticeAsRecorded}>{noticeAsRecorded} (as recorded)</option>
              )}
            </Select>
          </Field>
        </div>
        <ChipListField
          label="Direct reports"
          values={draft.directReports}
          placeholder="Add a seat, e.g. Head of Treasury"
          max={30}
          maxLength={160}
          onChange={(directReports) => update({ directReports })}
        />
      </SectionCard>

      <SectionCard
        title="Compensation shape"
        aside="The package's shape, never its figures. The salary band is the client's budget, set on each mandate"
      >
        <div className="grid grid-cols-1 gap-x-4 md:grid-cols-2">
          <Field label="Currency">
            <Select value={draft.currency} onChange={(e) => update({ currency: e.target.value })} className="!bg-panel">
              {[...new Set([draft.currency, ...CURRENCIES])].map((currency) => (
                <option key={currency}>{currency}</option>
              ))}
            </Select>
          </Field>
          <div className="mb-4">
            <span className="mb-1.5 block font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3">
              Base salary quoted
            </span>
            <SegmentedControl<BaseSalaryMode>
              label="Base salary quoted"
              value={draft.baseSalaryMode}
              options={Object.entries(BASE_SALARY_MODE_LABELS).map(([value, label]) => ({
                value: value as BaseSalaryMode,
                label,
              }))}
              onChange={(baseSalaryMode) => update({ baseSalaryMode })}
            />
          </div>
          <Field label="Target bonus">
            <span className="flex gap-2">
              <Input
                type="number"
                min={0}
                step={0.01}
                value={draft.bonusValue ?? ""}
                onChange={(e) => update({ bonusValue: numberOrNull(e.target.value) })}
                className="w-24 flex-none !bg-panel"
              />
              <Select
                value={draft.bonusBasis ?? ""}
                aria-label="Bonus basis"
                onChange={(e) => update({ bonusBasis: (e.target.value || null) as BonusBasis | null })}
                className="!bg-panel"
              >
                <option value="">Not set</option>
                {Object.entries(BONUS_BASIS_LABELS).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </Select>
            </span>
          </Field>
          <Field label="Long-term incentive">
            <Select
              value={draft.incentiveType ?? ""}
              onChange={(e) => update({ incentiveType: (e.target.value || null) as IncentiveType | null })}
              className="!bg-panel"
            >
              <option value="">None</option>
              {Object.entries(INCENTIVE_TYPE_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </Field>
        </div>
        <Field label="Incentive vesting">
          <Input
            value={draft.incentiveVesting}
            maxLength={200}
            onChange={(e) => update({ incentiveVesting: e.target.value })}
            className="!bg-panel"
          />
        </Field>
        <BenefitLines benefits={draft.benefits} onChange={(benefits) => update({ benefits })} />
      </SectionCard>

      <SectionCard title="Assessment" aside="What a candidate is scored against. Each panel with competencies must total exactly 100%">
        <div className="mb-4 flex flex-col gap-3.5">
          <CompetencyPanel
            title="Technical competencies"
            accent="sky"
            rows={draft.technical}
            locked={lockedTechnical}
            onChange={(technical) => update({ technical })}
            onToggleLock={(id) => setLockedTechnical(toggle(lockedTechnical, id))}
            onReorder={(fromId, toId) => update({ technical: moveRow(draft.technical, fromId, toId) })}
          />
          <CompetencyPanel
            title="Behavioural competencies"
            accent="amber"
            rows={draft.behavioural}
            locked={lockedBehavioural}
            onChange={(behavioural) => update({ behavioural })}
            onToggleLock={(id) => setLockedBehavioural(toggle(lockedBehavioural, id))}
            onReorder={(fromId, toId) => update({ behavioural: moveRow(draft.behavioural, fromId, toId) })}
          />
        </div>
        <CriteriaCard criteria={draft.criteria} onChange={(criteria) => update({ criteria })} />
      </SectionCard>

      {dirty && problems.length > 0 && (
        <ul className="mb-3 flex flex-col gap-0.5 font-mono text-[11.5px] text-red">
          {problems.map((problem) => (
            <li key={problem}>{problem}</li>
          ))}
        </ul>
      )}

      <div className="flex flex-wrap items-center gap-2.5 pt-1">
        {lifecycleAction && (
          <Button
            variant="secondary"
            className={cn(LIFECYCLE[lifecycleAction].destructive && "!border-red !text-red hover:!bg-red-dim")}
            loading={lifecycle.isPending}
            onClick={() => handleLifecycle(lifecycleAction)}
          >
            {LIFECYCLE[lifecycleAction].label}
          </Button>
        )}
        <div className="ms-auto">{saveButton}</div>
      </div>

      {confirming && (
        <Modal open onClose={() => setConfirming(null)} title={LIFECYCLE[confirming].label}>
          <p className="mb-5 text-[13px] text-text2">{LIFECYCLE[confirming].confirm}</p>
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setConfirming(null)}>
              Cancel
            </Button>
            <Button
              className="!border-red !bg-red !text-white hover:!brightness-105"
              loading={lifecycle.isPending}
              onClick={() => lifecycle.mutate(confirming)}
            >
              {LIFECYCLE[confirming].label}
            </Button>
          </div>
        </Modal>
      )}
    </>
  );
}

/** Asks before leaving with unsaved edits. The router here has no blocker, so the link guards itself. */
function BackLink({ path, heading, dirty }: { path: string; heading: string; dirty: boolean }) {
  return (
    <Link
      to={path}
      onClick={(event) => {
        if (dirty && !window.confirm(DISCARD)) event.preventDefault();
      }}
      className="inline-flex items-center gap-1.5 text-xs font-medium text-text3 transition hover:text-text"
    >
      <Icon d={ICONS.back} size={14} />
      {heading}
    </Link>
  );
}

function SectionCard({ title, aside, children }: { title: string; aside: string; children: ReactNode }) {
  return (
    <section aria-label={title} className="mb-4 rounded-[10px] border border-line-soft bg-panel2 p-5">
      <div className="text-[13px] font-semibold">{title}</div>
      <p className="mb-4 mt-0.5 font-mono text-[11.5px] text-text3">{aside}</p>
      {children}
    </section>
  );
}

function numberOrNull(text: string): number | null {
  if (text.trim() === "") return null;
  const value = Number(text);
  return Number.isFinite(value) ? value : null;
}

function lifecycleOf(scope: TemplateScope, detail: TemplateDetail): Lifecycle | null {
  if (scope === "library") {
    if (detail.fallback) return null;
    return detail.active ? "archive" : "restore";
  }
  switch (detail.origin) {
    case "CUSTOMISED":
      return "reset";
    case "OWN":
      return "delete";
    case "HIDDEN":
      return "show";
    default:
      return detail.fallback ? null : "hide";
  }
}

/** A firm looking at LightMove's own version of a template, which its first save turns into a copy. */
function isLibraryVersion(scope: TemplateScope, detail: TemplateDetail): boolean {
  return scope === "workspace" && (detail.origin === "LIBRARY" || detail.origin === "HIDDEN");
}

function saveLabelOf(scope: TemplateScope, detail: TemplateDetail | null): string {
  if (!detail) return "Create template";
  return isLibraryVersion(scope, detail) ? "Save as my firm's copy" : "Save";
}

function savedMessage(scope: TemplateScope, detail: TemplateDetail | null): string {
  if (!detail) return "Template created";
  if (scope === "library") return "Saved — live for every workspace without its own copy";
  return isLibraryVersion(scope, detail) ? "Saved as your firm's copy" : "Saved";
}

function lifecycleMessage(action: Lifecycle): string {
  switch (action) {
    case "archive":
      return "Archived — no workspace can pick it now";
    case "restore":
      return "Restored to the library";
    case "hide":
      return "Hidden from your firm's picker";
    case "show":
      return "Back in your firm's picker";
    case "reset":
      return "Reset to the library version";
    case "delete":
      return "Template deleted";
  }
}

function metaLineOf(scope: TemplateScope, detail: TemplateDetail | null): string {
  if (!detail) return "Not saved yet";
  const date = formatInstantDate(detail.revisedAt) ?? "—";
  if (isLibraryVersion(scope, detail)) {
    return `${detail.code} · LightMove library · updated ${date}`;
  }
  return `${detail.code} · last saved${detail.revisedByName ? ` by ${detail.revisedByName}` : ""}, ${date}`;
}

function bannerOf(
  scope: TemplateScope,
  detail: TemplateDetail | null,
): { tone: keyof typeof BANNER_TONES; text: string } {
  if (!detail) {
    return scope === "library"
      ? { tone: "sky", text: "A new library template. Once saved, every workspace sees it in the picker." }
      : { tone: "plain", text: "A new template for your firm only." };
  }
  if (scope === "library") {
    if (detail.fallback) {
      return {
        tone: "sky",
        text: "LightMove library — and the fallback: a role title no keyword matches is drafted from this template, so it can't be archived.",
      };
    }
    if (!detail.active) {
      return {
        tone: "plain",
        text: "Archived. No workspace sees it in the picker or matches a title against it until it is restored. Workspace copies are unaffected.",
      };
    }
    const copies = detail.customisedByWorkspaces ?? 0;
    const keeping =
      copies === 0 ? "" : ` — ${copies} workspace${copies === 1 ? " has its" : "s have their"} own copy and keep${copies === 1 ? "s" : ""} it`;
    return {
      tone: "sky",
      text: `LightMove library. Saving updates every workspace that hasn't customised this template${keeping}. Mandates already drafted never change.`,
    };
  }
  switch (detail.origin) {
    case "CUSTOMISED":
      return detail.libraryChangedSinceCustomised
        ? {
            tone: "amber",
            text: "Your firm's copy. LightMove has updated the library version since you customised it. Reset to take the new version, or keep yours.",
          }
        : { tone: "plain", text: "Your firm's copy of a library template. Library updates don't reach it until you reset." };
    case "OWN":
      return { tone: "plain", text: "Your firm's own template — it isn't in the LightMove library." };
    case "HIDDEN":
      return {
        tone: "plain",
        text: "Hidden from your firm's picker and title matching. Saving an edit makes it your firm's own copy.",
      };
    default:
      return {
        tone: "sky",
        text: "You're viewing the LightMove library version. Saving creates your firm's own copy; library updates stop reaching it until you reset.",
      };
  }
}
