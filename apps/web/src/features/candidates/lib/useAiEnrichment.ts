import { useMutation, useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as candidatesApi from "../api/candidatesApi";

const POLL_EVERY_MS = 3_000;
const GIVE_UP_AFTER_MS = 90_000;

interface RunMarks {
  assessedAt: string | null;
  failedAt: string | null;
}

/**
 * The AI deep enrich button and the assessment it produces, for one executive. Staff-only: the read
 * is not even asked for without `canWrite`, because the server refuses it to a client seat.
 *
 * <p>The run is queued (202) and lands later, as a new `assessedAt` or — when it produced nothing — a
 * new `failedAt`. The stream refreshes the read when either is written; the poll is the fallback for
 * a stream that is reconnecting, and the 90-second give-up only a safety net for a worker that died.
 */
export function useAiEnrichment(projectId: string, candidateId: string, canWrite: boolean) {
  const toast = useToast();
  // The marks on screen when the button was pressed: a different one means the run has finished.
  // Compared with themselves rather than with the browser's clock, which need not agree with the server's.
  const [pressedOver, setPressedOver] = useState<RunMarks | null>(null);

  const read = useQuery({
    queryKey: candidatesApi.AI_ASSESSMENT_KEY(projectId, candidateId),
    queryFn: ({ signal }) => candidatesApi.getAiAssessment(projectId, candidateId, signal),
    enabled: canWrite,
    refetchInterval: pressedOver === null ? false : POLL_EVERY_MS,
  });

  const current: RunMarks = {
    assessedAt: read.data?.assessedAt ?? null,
    failedAt: read.data?.failedAt ?? null,
  };
  const landed = pressedOver !== null && current.assessedAt !== pressedOver.assessedAt;
  const failed = pressedOver !== null && !landed && current.failedAt !== pressedOver.failedAt;

  useEffect(() => {
    if (landed) setPressedOver(null);
  }, [landed]);

  useEffect(() => {
    if (!failed) return;
    setPressedOver(null);
    toast("The AI enrichment failed — try again");
  }, [failed, toast]);

  const isRunning = pressedOver !== null && !landed && !failed;

  useEffect(() => {
    if (!isRunning) return;
    const timer = setTimeout(() => {
      setPressedOver(null);
      toast("The AI enrichment is taking longer than expected — try again in a minute");
    }, GIVE_UP_AFTER_MS);
    return () => clearTimeout(timer);
  }, [isRunning, toast]);

  const run = useMutation({
    mutationFn: () => candidatesApi.requestAiEnrich(projectId, candidateId),
    onMutate: () => setPressedOver(current),
    onSuccess: () => toast("Enriching with AI…"),
    onError: (error) => {
      setPressedOver(null);
      toast(messageFor(error));
    },
  });

  const assessedAt = read.data?.assessedAt ?? null;
  return {
    /** The last successful assessment, or null when no run has succeeded yet. */
    assessment: read.data && assessedAt !== null ? { ...read.data, assessedAt } : null,
    /** When the last run failed; the server clears it on a success, so it is always the newer news. */
    lastFailedAt: read.data?.failedAt ?? null,
    isLoading: read.isPending && canWrite,
    isError: read.isError,
    isRunning: isRunning || run.isPending,
    start: () => run.mutate(),
  };
}

export type AiEnrichment = ReturnType<typeof useAiEnrichment>;
