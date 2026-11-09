import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Select, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as dataRetentionApi from "../api/dataRetentionApi";
import type { CandidateRetention } from "../api/dataRetentionApi";
import { NEVER, periodLabel, retentionNote, savedToast } from "../lib/dataRetention";

/** How long a candidate nobody is working with stays before they wait for review (`Settings.dc.html`, `data`). */
export function CandidateRetentionCard({ saved }: { saved: CandidateRetention }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [choice, setChoice] = useState<number | null>(saved.months);

  const save = useMutation({
    mutationFn: () => dataRetentionApi.putCandidateRetention(choice),
    onSuccess: (next) => {
      queryClient.setQueryData(dataRetentionApi.CANDIDATE_RETENTION_KEY, next);
      void queryClient.invalidateQueries({ queryKey: dataRetentionApi.RETENTION_DUE_KEY });
      toast(savedToast(next.months));
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  return (
    <section aria-label="Candidate retention" className="rounded-u-card border border-u-border bg-u-raised p-5">
      <div className="text-body font-semibold text-u-text">Candidate retention</div>
      <p className="mt-0.5 font-mono text-meta text-u-text3">How long a candidate stays once nobody is working with them.</p>
      <label className="mt-3.5 flex flex-wrap items-center gap-2.5 text-note text-u-text2">
        <span>Keep candidates untouched for</span>
        <Select
          aria-label="Retention period"
          density="compact"
          className="w-auto"
          value={choice === null ? NEVER : String(choice)}
          onChange={(event) => setChoice(event.target.value === NEVER ? null : Number(event.target.value))}
        >
          {saved.options.map((months) => (
            <option key={months} value={months}>
              {periodLabel(months)}
            </option>
          ))}
          <option value={NEVER}>{periodLabel(null)}</option>
        </Select>
        <span>after their last activity, once they're in no live position.</span>
      </label>
      <div className="mt-3.5 flex gap-2.5 rounded-u-chip border border-u-border bg-u-surface px-3 py-2.5 font-mono text-meta text-u-text2">
        <Icon d={ICONS.info} size={15} className="mt-px flex-none text-u-text3" />
        <span>{retentionNote(choice)}</span>
      </div>
      <div className="mt-4 flex justify-end">
        <Button
          type="button"
          size="md"
          loading={save.isPending}
          disabled={choice === saved.months}
          onClick={() => save.mutate()}
        >
          Save changes
        </Button>
      </div>
    </section>
  );
}
