import { useMutation, useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as candidatesApi from "../api/candidatesApi";

const POLL_EVERY_MS = 3_000;
const GIVE_UP_AFTER_MS = 90_000;

/**
 * The AI deep enrich button and the assessment it produces, for one executive. Staff-only: the read
 * is not even asked for without `canWrite`, because the server refuses it to a client seat.
 *
 * <p>The run is queued (202) and lands later. The stream refreshes the read when it does; the poll
 * is the fallback for a stream that is reconnecting, and stops at the first assessment other than
 * the one on screen at the press — or after 90 seconds, when the reader is told to try again.
 */
export function useAiEnrichment(projectId: string, candidateId: string, canWrite: boolean) {
  const toast = useToast();
  // The assessedAt on screen when the button was pressed: a different one means the run landed.
  // Compared with itself rather than with the browser's clock, which need not agree with the server's.
  const [pressedOver, setPressedOver] = useState<{ assessedAt: string | null } | null>(null);

  const assessment = useQuery({
    queryKey: candidatesApi.AI_ASSESSMENT_KEY(projectId, candidateId),
    queryFn: ({ signal }) => candidatesApi.getAiAssessment(projectId, candidateId, signal),
    enabled: canWrite,
    refetchInterval: pressedOver === null ? false : POLL_EVERY_MS,
  });

  const current = assessment.data?.assessedAt ?? null;
  const isRunning = pressedOver !== null && current === pressedOver.assessedAt;

  useEffect(() => {
    if (pressedOver !== null && !isRunning) setPressedOver(null);
  }, [pressedOver, isRunning]);

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
    onMutate: () => setPressedOver({ assessedAt: current }),
    onSuccess: () => toast("Enriching with AI…"),
    onError: (error) => {
      setPressedOver(null);
      toast(messageFor(error));
    },
  });

  return {
    assessment: assessment.data ?? null,
    isLoading: assessment.isPending && canWrite,
    isError: assessment.isError,
    isRunning: isRunning || run.isPending,
    start: () => run.mutate(),
  };
}

export type AiEnrichment = ReturnType<typeof useAiEnrichment>;
