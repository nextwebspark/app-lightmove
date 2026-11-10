import { type ReactNode, useState } from "react";
import { cn } from "../../../lib/cn";
import type { CompetencyPanel, RankingCriterion } from "../api/types";
import {
  changedCriteria,
  criteriaOf,
  effectiveOverall,
  focusCriterion,
  hasBothPanels,
  PANELS,
  panelsOffHundred,
  panelWeightTotal,
  type Weighting,
} from "../lib/ranking";
import { splitLabel } from "../lib/rankingWording";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { ReportPanel } from "./ReportCard";

const PANEL_LABEL: Record<CompetencyPanel, string> = { technical: "Technical", behavioural: "Behavioural" };
const PANEL_TEXT: Record<CompetencyPanel, string> = { technical: "text-u-inferred", behavioural: "text-u-signal" };
const PANEL_FILL: Record<CompetencyPanel, string> = { technical: "bg-u-inferred", behavioural: "bg-u-signal" };
const STEP = 5;

const formatPercent = (value: number) => `${Math.round(value * 10) / 10}%`;

interface WeightingProps {
  criteria: readonly RankingCriterion[];
  baseline: Weighting;
  weighting: Weighting;
  /** Left out, the card only shows the weighting in force. */
  onChange?: (next: Weighting) => void;
}

/** The technical/behavioural split, beside the insight it moves. */
export function GroupWeighting({ criteria, baseline, weighting, onChange }: WeightingProps) {
  const splitMoves = hasBothPanels(criteria);
  const technical = splitMoves
    ? weighting.share
    : criteria.some((criterion) => criterion.panel === "technical")
      ? 100
      : 0;
  const setShare = (share: number) => onChange?.({ ...weighting, share });

  return (
    <ReportPanel>
      <div className="flex flex-col gap-3">
        <WeightingHeader title="Weighting">
          <span className="rounded-full bg-u-inferred-tint px-2.5 py-1 text-meta text-u-inferred">
            Scenario {splitLabel(technical)}
          </span>
        </WeightingHeader>
        <div className="flex justify-between text-note text-u-text2">
          <span>Group weighting</span>
          <span>Baseline {splitLabel(baseline.share)}</span>
        </div>
        <div className="flex items-end justify-between">
          <div className="flex flex-col gap-0.5 text-u-inferred">
            <span className="font-u-num text-figure font-semibold">{technical}%</span>
            <span className="text-body">Technical</span>
          </div>
          <div className="flex flex-col items-end gap-0.5 text-u-signal">
            <span className="font-u-num text-figure font-semibold">{100 - technical}%</span>
            <span className="text-body">Behavioural</span>
          </div>
        </div>
        <SplitSlider
          value={technical}
          disabled={!onChange || !splitMoves}
          onChange={setShare}
          label="Technical weight"
        />
        <div className="text-meta text-u-text2">
          {splitMoves
            ? `Overall = 10 × (${(technical / 100).toFixed(2)} × Technical + ${((100 - technical) / 100).toFixed(2)} × Behavioural)`
            : "The brief scores one group of competencies, so the split moves nothing."}
        </div>
      </div>
    </ReportPanel>
  );
}

/** Each competency's weight within its group, and what that makes it worth overall. */
export function CompetencyWeights({ criteria, weighting, onChange }: Omit<WeightingProps, "baseline">) {
  const [showWeights, setShowWeights] = useState(true);
  const changed = changedCriteria(criteria, weighting.weights);
  const offHundred = panelsOffHundred(criteria, weighting.weights);
  const noted = focusCriterion(criteria, weighting.weights) ?? [...criteria].sort((x, y) => y.weight - x.weight)[0];
  const setWeight = (key: string, weight: number) =>
    onChange?.({ ...weighting, weights: { ...weighting.weights, [key]: weight } });

  return (
    <ReportPanel>
      <WeightingHeader title="Competency weights">
        {changed.length > 0 && (
          <span className="rounded-full bg-u-signal-tint px-2.5 py-1 text-meta text-u-signal">
            {changed.length} changed
          </span>
        )}
        <button
          type="button"
          onClick={() => setShowWeights((shown) => !shown)}
          aria-expanded={showWeights}
          aria-label={showWeights ? "Hide competency weights" : "Show competency weights"}
          className="flex p-0.5 text-u-text2 transition hover:text-u-text"
        >
          <Icon d={ICONS.chevronDown} size={16} className={cn("transition", showWeights && "rotate-180")} />
        </button>
      </WeightingHeader>
      <p className="mt-3 text-note text-u-text2">
        Adjustments stay active while hidden.{" "}
        {PANELS.map((panel) =>
          criteriaOf(criteria, panel).length === 0
            ? null
            : `${PANEL_LABEL[panel]} ${criteriaOf(criteria, panel)
                .map((criterion) => weighting.weights[criterion.key])
                .join("/")}`,
        )
          .filter(Boolean)
          .join(" · ")}
        .
      </p>
      {showWeights && (
        <div className="mt-3 flex flex-col gap-4">
          {PANELS.map((panel, index) => {
            const panelCriteria = criteriaOf(criteria, panel);
            if (panelCriteria.length === 0) return null;
            const total = panelWeightTotal(criteria, panel, weighting.weights);
            const isOff = offHundred.includes(panel);
            return (
              <div key={panel} className={cn("flex flex-col gap-4", index > 0 && "border-t border-u-border pt-4")}>
                <div className="flex justify-between gap-2.5">
                  <span className="text-note font-semibold">{PANEL_LABEL[panel]}</span>
                  <span className={cn("text-meta", isOff ? "text-u-offlimits" : PANEL_TEXT[panel])}>
                    Within-group · {total}%{isOff && " · must total 100%"}
                  </span>
                </div>
                {panelCriteria.map((criterion) => (
                  <div key={criterion.key} className="flex flex-col gap-2">
                    <div className="flex justify-between gap-2.5">
                      <span className="text-note text-u-text2">{criterion.name}</span>
                      <span className={cn("font-u-num text-body font-semibold", PANEL_TEXT[panel])}>
                        {weighting.weights[criterion.key]}%
                      </span>
                    </div>
                    <WeightSlider
                      value={weighting.weights[criterion.key]}
                      fill={PANEL_FILL[panel]}
                      label={criterion.name}
                      disabled={!onChange}
                      onChange={(weight) => setWeight(criterion.key, weight)}
                    />
                    <span className="text-meta text-u-text2">
                      Effective overall: {formatPercent(effectiveOverall(criterion, criteria, weighting))}
                    </span>
                  </div>
                ))}
              </div>
            );
          })}
          {noted && (
            <p className="text-meta text-u-text2">
              {noted.name} {changed.includes(noted) ? "now contributes" : "contributes"}{" "}
              {formatPercent(effectiveOverall(noted, criteria, weighting))} overall.
            </p>
          )}
        </div>
      )}
    </ReportPanel>
  );
}

/** The one header both weighting cards wear, so the two read as a pair. */
function WeightingHeader({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="flex items-center gap-2.5">
      <h2 className="flex-1 text-lead font-bold">{title}</h2>
      {children}
    </div>
  );
}

function SplitSlider({
  value,
  disabled,
  onChange,
  label,
}: {
  value: number;
  disabled: boolean;
  onChange: (value: number) => void;
  label: string;
}) {
  return (
    <div className="relative mb-0.5 mt-1.5 h-[18px]">
      <div
        className="absolute inset-x-0 top-[5px] h-2 rounded-full"
        style={{
          background: `linear-gradient(to right, var(--color-u-inferred) 0 ${value}%, var(--color-u-signal) ${value}% 100%)`,
        }}
      />
      <div
        className="absolute top-px size-4 rounded-full bg-u-text shadow-[inset_0_0_0_4px_var(--color-u-inferred-tint)]"
        style={{ left: `calc(${value}% - 8px)` }}
      />
      <input
        type="range"
        min={0}
        max={100}
        step={STEP}
        value={value}
        disabled={disabled}
        aria-label={label}
        onChange={(event) => onChange(Number(event.target.value))}
        className="absolute inset-0 m-0 size-full cursor-pointer opacity-0 disabled:cursor-default"
      />
    </div>
  );
}

function WeightSlider({
  value,
  fill,
  label,
  disabled,
  onChange,
}: {
  value: number;
  fill: string;
  label: string;
  disabled: boolean;
  onChange: (value: number) => void;
}) {
  return (
    <div className="relative h-2 rounded-full bg-u-raised">
      <div className={cn("h-full rounded-full", fill)} style={{ width: `${value}%` }} />
      <input
        type="range"
        min={0}
        max={100}
        step={STEP}
        value={value}
        disabled={disabled}
        aria-label={label}
        onChange={(event) => onChange(Number(event.target.value))}
        className="absolute -top-1.5 left-0 m-0 h-5 w-full cursor-pointer opacity-0 disabled:cursor-default"
      />
    </div>
  );
}
