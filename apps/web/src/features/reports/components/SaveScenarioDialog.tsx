import { Button, Field, Modal } from "../../../components/ui";
import type { RankingCriterion } from "../api/types";
import { changedCriteria, type Weighting } from "../lib/ranking";
import { splitLabel } from "../lib/rankingWording";

const MAX_RATIONALE = 1000;

/** Asks why before the team's scenario is saved; a weighting back at the baseline clears it instead. */
export function SaveScenarioDialog({
  open,
  criteria,
  baseline,
  weighting,
  atBaseline,
  rationale,
  saving,
  onRationale,
  onCancel,
  onSave,
}: {
  open: boolean;
  criteria: readonly RankingCriterion[];
  baseline: Weighting;
  weighting: Weighting;
  atBaseline: boolean;
  rationale: string;
  saving: boolean;
  onRationale: (rationale: string) => void;
  onCancel: () => void;
  onSave: () => void;
}) {
  const changed = changedCriteria(criteria, weighting.weights);
  return (
    <Modal
      open={open}
      onClose={onCancel}
      onSubmit={onSave}
      title={atBaseline ? "Return to the agreed baseline?" : "Save scenario"}
      subtitle="Shared with everyone on this position. The brief's weights are unchanged."
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onCancel} disabled={saving}>
            Cancel
          </Button>
          <Button type="submit" loading={saving}>
            {atBaseline ? "Clear scenario" : "Save"}
          </Button>
        </div>
      }
    >
      <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-6 gap-y-2 rounded-lg bg-u-raised px-4 py-3 text-body">
        <dt className="text-u-text3">Technical : behavioural</dt>
        <dd className="text-right">
          <span className="font-u-num">{splitLabel(weighting.share)}</span>
          <span className="text-u-text3"> · baseline {splitLabel(baseline.share)}</span>
        </dd>
        <dt className="text-u-text3">Competency weights</dt>
        <dd className="text-right">
          {changed.length === 0 ? "As the brief" : `${changed.length} changed from the brief`}
        </dd>
      </dl>

      {atBaseline ? (
        <p className="mt-4 text-body text-u-text2">
          This weighting is the brief's own, so saving clears the team's saved scenario and everyone sees the agreed
          baseline.
        </p>
      ) : (
        <div className="mt-4">
          <Field label="Rationale (optional)">
            <textarea
              value={rationale}
              maxLength={MAX_RATIONALE}
              rows={4}
              onChange={(event) => onRationale(event.target.value)}
              placeholder="Why the team is trying these priorities"
              className="w-full resize-y rounded-md border border-u-border bg-u-sunken px-3 py-2 text-body placeholder:text-u-text3 focus:border-u-accent focus:outline-none"
            />
          </Field>
          <div className="mt-1 text-right text-meta text-u-text3">
            {rationale.length}/{MAX_RATIONALE}
          </div>
        </div>
      )}
    </Modal>
  );
}
