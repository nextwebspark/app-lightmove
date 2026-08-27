import type { Competency } from "../api/types";
import { rebalance } from "../lib/rebalance";
import { AddRowButton, RemoveRowButton } from "./fields";

/**
 * One weighting panel (technical = sky, behavioural = amber). Sliders rebalance the other rows so
 * the total holds; the number input sets a weight exactly. The total badge goes green only at 100.
 *
 * The description beside each name is what the competency measures for this mandate — a weight on
 * its own is a number the next reader has to interpret.
 */
export function CompetencyPanel({
  title,
  accent,
  rows,
  onChange,
}: {
  title: string;
  accent: "sky" | "amber";
  rows: Competency[];
  onChange: (rows: Competency[]) => void;
}) {
  const total = rows.reduce((sum, row) => sum + row.weight, 0);
  const dot = accent === "sky" ? "bg-sky" : "bg-amber-btn";
  const slider = accent === "sky" ? "accent-sky" : "accent-amber-btn";

  const patch = (index: number, changes: Partial<Competency>) =>
    onChange(rows.map((row, i) => (i === index ? { ...row, ...changes } : row)));

  return (
    <div
      className={`rounded-[10px] border p-4 ${
        accent === "sky" ? "border-sky/30 bg-sky-dim/40" : "border-amber-btn/35 bg-amber-dim/40"
      }`}
    >
      <div className="mb-3.5 flex items-center justify-between">
        <span className="flex items-center gap-[7px] text-[13px] font-semibold">
          <span className={`size-2 rounded-full ${dot}`} />
          {title}
        </span>
        <span
          className={`rounded-md px-2.5 py-0.5 font-mono text-sm font-bold ${
            total === 100 ? "bg-green-dim text-green" : "bg-red-dim text-red"
          }`}
        >
          {total}%
        </span>
      </div>

      {rows.map((row, index) => (
        <div key={index} className="mb-3.5">
          <div className="mb-1.5 flex items-center gap-2">
            <input
              value={row.name}
              aria-label={`${title} competency ${index + 1} name`}
              onChange={(e) => patch(index, { name: e.target.value })}
              className="min-w-0 flex-1 bg-transparent text-[13px] font-medium text-text outline-none"
            />
            <input
              type="number"
              min={0}
              max={100}
              value={row.weight}
              aria-label={`${row.name} weight`}
              onChange={(e) => {
                const weight = Math.max(0, Math.min(100, Math.round(Number(e.target.value) || 0)));
                patch(index, { weight });
              }}
              className="w-[52px] rounded-md border border-line bg-panel px-1.5 py-[3px] text-right font-mono text-xs font-semibold text-text outline-none"
            />
            <span className="font-mono text-xs font-medium text-text3">%</span>
          </div>
          <input
            value={row.description ?? ""}
            aria-label={`${row.name} description`}
            placeholder="What this measures…"
            onChange={(e) => patch(index, { description: e.target.value || null })}
            className="mb-1.5 w-full bg-transparent font-mono text-[11.5px] text-text3 outline-none placeholder:text-text3/60"
          />
          <div className="flex items-center gap-2">
            <input
              type="range"
              min={0}
              max={100}
              value={row.weight}
              aria-label={`${row.name} slider`}
              onChange={(e) => onChange(rebalance(rows, index, Number(e.target.value)))}
              className={`min-w-0 flex-1 ${slider}`}
            />
            {rows.length > 1 && (
              <RemoveRowButton
                label={`Remove ${row.name}`}
                onClick={() => onChange(rows.filter((_, i) => i !== index))}
              />
            )}
          </div>
        </div>
      ))}

      <AddRowButton
        className="w-full"
        onClick={() => onChange([...rows, { name: "New competency", description: null, weight: 0 }])}
      >
        + Add competency
      </AddRowButton>
    </div>
  );
}
