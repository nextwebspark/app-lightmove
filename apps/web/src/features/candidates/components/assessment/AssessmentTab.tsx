import { useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { LinesSkeleton, TextArea } from "../../../../components/ui";
import { SegmentedControl } from "../../../../components/ui/SegmentedControl";
import { cn } from "../../../../lib/cn";
import { messageFor } from "../../../../lib/errorCodes";
import { formatInstantDate } from "../../../../lib/format";
import { useWorkspaceVocabulary } from "../../../workspace/lib/vocabulary";
import { AiActionButton } from "../AiActionButton";
import { AiInferredBadge } from "../CandidateFieldGroups";
import type { ScorecardCriterion, ScorecardPanel } from "../../api/scorecardTypes";
import { criterionKey, type LivePanel } from "../../lib/scorecardScoring";
import type { ReportSection, ScorecardEditor } from "../../lib/useScorecardEditor";

type AssessmentView = "scores" | "report";

const VIEWS = [
  { value: "scores", label: "Competency scores" },
  { value: "report", label: "Assessment report" },
] as const;

const PANEL_LOOK: Record<ScorecardPanel, { label: string; dot: string; text: string; tint: string }> = {
  technical: { label: "Technical", dot: "bg-u-inferred", text: "text-u-inferred", tint: "bg-u-inferred-tint" },
  behavioural: { label: "Behavioural", dot: "bg-u-signal", text: "text-u-signal", tint: "bg-u-signal-tint" },
};

const MAX_EVIDENCE = 2000;
const MAX_SECTION = 4000;
/** Where a first press of + lands: the middle of the scale, one step either way from a view. */
const FIRST_SCORE = 5;
const AI_DRAFT_TITLE = "AI-drafted — change it and it is yours";

/**
 * The drawer's Assessment tab (`Position.dc.html`, Assessment): the consultant's own scores against the brief's
 * competencies, totalled out of 100 as they move, and a written report in five sections. Saved from the footer
 * — Save draft, or Mark reviewed & submit. Assess with AI drafts whatever nobody has written, never a consultant's
 * own value. Staff-only: it ranks a person.
 */
export function AssessmentTab({ projectId, editor }: { projectId: string; editor: ScorecardEditor }) {
  const [view, setView] = useState<AssessmentView>("scores");
  const { saved, tally } = editor;

  if (saved.isError) return <p className="py-4 text-note text-u-text3">{messageFor(saved.error)}</p>;
  if (saved.isPending || !tally) {
    return (
      <div className="py-4">
        <LinesSkeleton />
      </div>
    );
  }
  const scorecard = saved.data;

  if (tally.criteriaCount === 0 && scorecard.retired.length === 0) {
    return (
      <div className="flex flex-col items-start gap-2 py-6">
        <p className="text-body font-semibold text-u-text">Nothing to score against yet</p>
        <p className="text-note text-u-text3">
          The brief has no competencies. Add them in the position's Assessment Criteria step, and they arrive
          here weighted and ready to score.
        </p>
        <Link
          to={`/projects/${projectId}?step=assessment`}
          className="mt-1 inline-flex items-center gap-1 text-note font-semibold text-u-accent hover:underline"
        >
          Open the brief's criteria
          <Icon d={ICONS.arrowRight} size={12} />
        </Link>
      </div>
    );
  }

  const status = scorecard.status === "reviewed" && !editor.isDirty ? "reviewed" : "draft";

  return (
    <div className="pb-4">
      {editor.stale && (
        <Banner tone="danger">
          <span className="flex-1">
            Someone saved this assessment after you opened it — a teammate, or the AI. Your changes are still here.
          </span>
          <BannerButton onClick={editor.reload}>Reload, keeping mine</BannerButton>
        </Banner>
      )}
      {editor.restored && editor.isDirty && !editor.stale && (
        <Banner tone="info">
          <span className="flex-1">Your unsaved changes from earlier are back.</span>
          <BannerButton onClick={editor.discard}>Discard</BannerButton>
        </Banner>
      )}

      <div className="mt-3 flex flex-wrap items-center justify-between gap-x-3 gap-y-1.5">
        <span className="text-meta text-u-text3">
          {scorecard.aiDraftedAt
            ? `AI drafted ${formatInstantDate(scorecard.aiDraftedAt)} · it never changes a score or section you wrote`
            : "The AI scores each competency from the profile, your notes and the documents on file, and drafts the report"}
        </span>
        <AiActionButton
          label="Assess with AI"
          busyLabel="Assessing…"
          isBusy={editor.isAssessing}
          disabled={editor.isDirty || editor.isSaving || tally.criteriaCount === 0}
          title={
            editor.isDirty
              ? "Save or discard your changes first"
              : "Score every open competency from the profile, notes and documents, with the evidence, and draft the report"
          }
          onClick={editor.assessWithAi}
        />
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-x-5 gap-y-3 border-y border-u-border py-3.5">
        <div className="min-w-24 flex-1">
          <div className="type-micro-label font-mono text-u-text3">Overall score</div>
          <div className="mt-0.5 flex items-baseline gap-1.5">
            <span className="type-figure text-u-text">{formatPoints(tally.overall)}</span>
            <span className="text-note text-u-text3">/ 100</span>
          </div>
        </div>
        {tally.panels
          .filter((panel) => panel.criteria.length > 0)
          .map((panel) => (
            <div key={panel.panel} className="border-s border-u-border ps-4">
              <div className={cn("type-micro-label font-mono", PANEL_LOOK[panel.panel].text)}>
                {PANEL_LOOK[panel.panel].label}
              </div>
              <div className="mt-0.5 flex items-baseline gap-1">
                <span className={cn("text-lead font-semibold", PANEL_LOOK[panel.panel].text)}>
                  {formatPoints(panel.points)}
                </span>
                <span className="font-mono text-meta text-u-text3">/{panel.share}</span>
              </div>
            </div>
          ))}
        <StatusChip status={status} scorecard={scorecard} isDirty={editor.isDirty} />
      </div>

      <SegmentedControl label="Assessment view" options={VIEWS} value={view} onChange={setView} className="mt-3" />

      {view === "scores" ? (
        <>
          <div className="mt-3 flex items-center justify-between gap-3 border-y border-u-border py-2">
            <span className="text-meta text-u-text3">
              Scores out of 10 · {tally.scoredCount} / {tally.criteriaCount} criteria scored
            </span>
            <span aria-hidden="true" className="flex gap-1">
              {tally.panels.flatMap((panel) =>
                panel.criteria.map((criterion) => (
                  <span
                    key={criterionKey(criterion.panel, criterion.name)}
                    className={cn(
                      "size-1.5 rounded-full",
                      criterion.score === null ? "bg-u-border-strong" : PANEL_LOOK[panel.panel].dot,
                    )}
                  />
                )),
              )}
            </span>
          </div>
          {tally.panels
            .filter((panel) => panel.criteria.length > 0)
            .map((panel) => (
              <PanelSection key={panel.panel} panel={panel} editor={editor} />
            ))}
          {scorecard.retired.length > 0 && <RetiredScores criteria={scorecard.retired} />}
          <div className="flex flex-wrap items-center justify-between gap-3 pt-4">
            <span className="text-note text-u-text2">Ready to capture your written assessment?</span>
            <button
              type="button"
              onClick={() => setView("report")}
              className="inline-flex items-center gap-1.5 rounded-u-control border border-u-border-strong bg-u-surface px-3 py-1.5 text-note font-medium text-u-text hover:border-u-text3"
            >
              <Icon d={ICONS.pencil} size={12} />
              Write assessment report
            </button>
          </div>
        </>
      ) : (
        <ReportSections editor={editor} />
      )}
    </div>
  );
}

function PanelSection({ panel, editor }: { panel: LivePanel; editor: ScorecardEditor }) {
  const look = PANEL_LOOK[panel.panel];
  return (
    <section aria-label={`${look.label} competencies`} className="border-b border-u-border py-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="flex items-center gap-2">
          <span aria-hidden="true" className={cn("size-2 rounded-full", look.dot)} />
          <span className={cn("type-micro-label font-mono", look.text)}>{look.label}</span>
          <span className={cn("rounded-sm px-1.5 py-px font-mono text-meta", look.tint, look.text)}>
            {panel.share}% of total
          </span>
        </span>
        <span className="flex items-baseline gap-1">
          <span className={cn("text-body font-semibold", look.text)}>
            {panel.average === null ? "—" : panel.average.toFixed(1)}
          </span>
          <span className="font-mono text-meta text-u-text3">
            /10 avg · {formatPoints(panel.points)}/{panel.share} pts
          </span>
        </span>
      </div>
      <div className="mt-2.5 hidden items-center px-3 pb-1 sm:flex" aria-hidden="true">
        <span className="type-micro-label min-w-0 flex-1 font-mono text-u-text3">Criterion</span>
        <span className="type-micro-label w-14 flex-none text-center font-mono text-u-text3">Weight</span>
        <span className="type-micro-label w-28 flex-none text-center font-mono text-u-text3">Score</span>
        <span className="type-micro-label w-14 flex-none text-end font-mono text-u-text3">Points</span>
      </div>
      <div className="mt-1 flex flex-col gap-2">
        {panel.criteria.map((criterion) => (
          <CriterionRow key={criterionKey(criterion.panel, criterion.name)} criterion={criterion} editor={editor} />
        ))}
      </div>
    </section>
  );
}

function CriterionRow({
  criterion,
  editor,
}: {
  criterion: LivePanel["criteria"][number];
  editor: ScorecardEditor;
}) {
  const [open, setOpen] = useState(false);
  const key = criterionKey(criterion.panel, criterion.name);
  const { score } = criterion;
  const look = PANEL_LOOK[criterion.panel];
  const bodyId = `criterion-${key.replace(/[^a-z0-9]+/g, "-")}`;
  const evidence = editor.evidenceOf(key);

  return (
    <div className="overflow-hidden rounded-u-control border border-u-border bg-u-surface">
      <div className="flex flex-wrap items-center gap-y-2 px-3 py-2.5">
        <button
          type="button"
          aria-expanded={open}
          aria-controls={bodyId}
          onClick={() => setOpen((shown) => !shown)}
          className="flex min-w-0 flex-1 items-center gap-1.5 text-start"
        >
          <Icon
            d={ICONS.chevronRight}
            size={12}
            className={cn("flex-none text-u-text3 transition-transform", open && "rotate-90")}
          />
          <span className="truncate text-note font-semibold text-u-text">{criterion.name}</span>
          {editor.isAiDrafted(key) && <AiInferredBadge title={AI_DRAFT_TITLE} />}
          {evidence.trim() && (
            <Icon d={ICONS.fileText} size={11} className="flex-none text-u-text3" aria-label="Has evidence" />
          )}
        </button>
        <span className="flex w-14 flex-none flex-col items-center">
          <span className={cn("font-mono text-meta font-medium", look.text)}>{Math.round(criterion.weightInPanel)}%</span>
          <span className="text-eyebrow text-u-text3">in group</span>
        </span>
        <ScoreStepper
          name={criterion.name}
          score={score}
          onChange={(next) => editor.setScore(key, next)}
        />
        <span className="flex w-14 flex-none flex-col items-end">
          <span className={cn("font-mono text-note font-semibold", score === null ? "text-u-text3" : look.text)}>
            {score === null ? "—" : formatPoints(criterion.points)}
          </span>
          <span className="font-mono text-eyebrow text-u-text3">/{formatPoints(criterion.maxPoints)}</span>
        </span>
      </div>
      {open && (
        <div id={bodyId} className="flex flex-col gap-2 border-t border-u-border bg-u-sunken px-3 py-3">
          {criterion.description && (
            <p className="text-note text-u-text2">
              <span className="font-semibold text-u-text3">Brief criterion: </span>
              {criterion.description}
            </p>
          )}
          <label className="flex flex-col gap-1">
            <span className="type-micro-label font-mono text-u-text3">Evidence / rationale</span>
            <TextArea
              rows={3}
              value={evidence}
              maxLength={MAX_EVIDENCE}
              onChange={(event) => editor.setEvidence(key, event.target.value)}
              placeholder="What you saw that earns this score — a role, a result, what a referee said…"
              className="text-note"
            />
          </label>
          <div className="flex items-center justify-between gap-3">
            <span className="text-meta text-u-text3">Missing evidence is an open question, not a low score.</span>
            {score !== null && (
              <button
                type="button"
                onClick={() => editor.setScore(key, null)}
                className="flex-none text-meta font-medium text-u-text3 hover:text-u-text"
              >
                Clear score
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

/** −, the score, +. Unscored reads "—"; the first + lands mid-scale, and Clear score takes it back to unscored. */
function ScoreStepper({
  name,
  score,
  onChange,
}: {
  name: string;
  score: number | null;
  onChange: (score: number | null) => void;
}) {
  const step = "grid h-7 w-7 place-items-center text-u-text3 hover:text-u-text disabled:opacity-35 disabled:hover:text-u-text3";
  return (
    <span className="flex w-28 flex-none justify-center">
      <span
        role="group"
        aria-label={`Score for ${name}`}
        className="flex h-7 items-center rounded-u-control border border-u-border bg-u-sunken"
      >
        <button
          type="button"
          aria-label={`Lower ${name}`}
          disabled={score === null || score <= 0}
          onClick={() => score !== null && onChange(score - 1)}
          className={step}
        >
          <Icon d="M5 12h14" size={12} />
        </button>
        <output
          aria-live="polite"
          aria-label={`${name} score`}
          className="w-9 border-x border-u-border text-center font-mono text-note font-semibold text-u-text"
        >
          {score ?? "—"}
        </output>
        <button
          type="button"
          aria-label={`Raise ${name}`}
          disabled={score !== null && score >= 10}
          onClick={() => onChange(score === null ? FIRST_SCORE : score + 1)}
          className={step}
        >
          <Icon d={ICONS.plus} size={12} />
        </button>
      </span>
    </span>
  );
}

function RetiredScores({ criteria }: { criteria: readonly ScorecardCriterion[] }) {
  const [open, setOpen] = useState(false);
  return (
    <section aria-label="No longer in the brief" className="border-b border-u-border py-3">
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen((shown) => !shown)}
        className="flex w-full items-center gap-1.5 text-start text-note font-medium text-u-text3 hover:text-u-text2"
      >
        <Icon d={ICONS.chevronRight} size={12} className={cn("transition-transform", open && "rotate-90")} />
        No longer in the brief
        <span className="font-mono text-meta">{criteria.length}</span>
        <span className="ms-auto text-meta">Kept, not counted</span>
      </button>
      {open && (
        <ul className="mt-2 flex flex-col gap-1.5">
          {criteria.map((criterion) => (
            <li
              key={criterionKey(criterion.panel, criterion.name)}
              className="flex items-start gap-2 rounded-u-control bg-u-sunken px-3 py-2 text-note"
            >
              <span className={cn("mt-1.5 size-1.5 flex-none rounded-full", PANEL_LOOK[criterion.panel].dot)} />
              <span className="min-w-0 flex-1">
                <span className="text-u-text2">{criterion.name}</span>
                {criterion.evidence && <span className="mt-0.5 block text-meta text-u-text3">{criterion.evidence}</span>}
              </span>
              <span className="flex-none font-mono text-u-text3">{criterion.score ?? "—"}/10</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function ReportSections({ editor }: { editor: ScorecardEditor }) {
  const vocabulary = useWorkspaceVocabulary();
  const sections: readonly { id: ReportSection; title: string; placeholder: string }[] = [
    {
      id: "careerSummary",
      title: "Career summary",
      placeholder: "The arc of their career, as it bears on this role.",
    },
    {
      id: "coreValue",
      title: `Core value to the ${vocabulary.unitLower}`,
      placeholder: "What they would bring that the brief asks for.",
    },
    {
      id: "concerns",
      title: "Concerns / areas to probe",
      placeholder: "Gaps, risks and what to ask at interview.",
    },
    {
      id: "workingStyle",
      title: "Personality & working style",
      placeholder: "How they lead and work with others — what you observed, not inferred.",
    },
    { id: "motivation", title: "Motivation for the role", placeholder: "Why this move, and why now." },
  ];
  const [open, setOpen] = useState<ReportSection | null>(() =>
    sections.find((section) => !editor.reportOf(section.id).trim())?.id ?? null,
  );

  return (
    <div className="flex flex-col gap-2 pt-3">
      {sections.map((section) => {
        const text = editor.reportOf(section.id);
        const isOpen = open === section.id;
        const bodyId = `report-${section.id}`;
        return (
          <div key={section.id} className="overflow-hidden rounded-u-control border border-u-border bg-u-surface">
            <button
              type="button"
              aria-expanded={isOpen}
              aria-controls={bodyId}
              onClick={() => setOpen(isOpen ? null : section.id)}
              className="flex w-full items-center gap-2 px-3 py-3 text-start"
            >
              <Icon
                d={ICONS.chevronRight}
                size={12}
                className={cn("flex-none text-u-text3 transition-transform", isOpen && "rotate-90")}
              />
              <span className="min-w-0 flex-1">
                <span className="flex items-center gap-1 text-note font-semibold text-u-text">
                  {section.title}
                  {editor.isReportAiDrafted(section.id) && <AiInferredBadge title={AI_DRAFT_TITLE} />}
                </span>
                {!isOpen && text.trim() && (
                  <span className="mt-0.5 block truncate text-meta text-u-text3">{text.trim()}</span>
                )}
              </span>
              <span
                className={cn(
                  "flex-none rounded-sm px-1.5 py-px font-mono text-eyebrow",
                  text.trim() ? "bg-u-raised text-u-text2" : "bg-u-signal-tint text-u-signal",
                )}
              >
                {text.trim() ? "Draft" : "Needs input"}
              </span>
            </button>
            {isOpen && (
              <div id={bodyId} className="px-3 pb-3">
                <TextArea
                  aria-label={section.title}
                  rows={5}
                  value={text}
                  maxLength={MAX_SECTION}
                  onChange={(event) => editor.setReport(section.id, event.target.value)}
                  placeholder={section.placeholder}
                  className="text-note"
                />
              </div>
            )}
          </div>
        );
      })}
      <p className="pt-2 text-meta text-u-text3">
        Missing evidence is an open question, not an automatic negative assessment. Your team sees this
        assessment; client contacts never do.
      </p>
    </div>
  );
}

function StatusChip({
  status,
  scorecard,
  isDirty,
}: {
  status: "draft" | "reviewed";
  scorecard: { reviewedAt: string | null; reviewedByName: string | null; updatedAt: string | null };
  isDirty: boolean;
}) {
  if (status === "reviewed") {
    return (
      <span className="flex flex-col items-end gap-0.5">
        <span className="inline-flex items-center gap-1 rounded-sm bg-u-direct-tint px-1.5 py-0.5 text-meta font-medium text-u-direct">
          <Icon d={ICONS.check} size={11} />
          Reviewed
        </span>
        <span className="font-mono text-eyebrow text-u-text3">
          {scorecard.reviewedByName ?? "Someone"} · {formatInstantDate(scorecard.reviewedAt)}
        </span>
      </span>
    );
  }
  return (
    <span className="flex flex-col items-end gap-0.5">
      <span className="rounded-sm bg-u-inferred-tint px-1.5 py-0.5 text-meta font-medium text-u-inferred">Draft</span>
      <span className="font-mono text-eyebrow text-u-text3">
        {isDirty ? "Unsaved changes" : scorecard.updatedAt ? `Saved ${formatInstantDate(scorecard.updatedAt)}` : "Not started"}
      </span>
    </span>
  );
}

function Banner({ tone, children }: { tone: "info" | "danger"; children: ReactNode }) {
  return (
    <div
      role={tone === "danger" ? "alert" : "status"}
      className={cn(
        "mt-3 flex items-center gap-2 rounded-u-control px-3 py-2 text-note",
        tone === "danger" ? "bg-u-offlimits-tint text-u-offlimits" : "bg-u-accent-tint text-u-accent",
      )}
    >
      {children}
    </div>
  );
}

function BannerButton({ onClick, children }: { onClick: () => void; children: ReactNode }) {
  return (
    <button type="button" onClick={onClick} className="flex-none font-semibold underline-offset-2 hover:underline">
      {children}
    </button>
  );
}

function formatPoints(points: number): string {
  return Number.isInteger(points) ? String(points) : points.toFixed(1);
}
