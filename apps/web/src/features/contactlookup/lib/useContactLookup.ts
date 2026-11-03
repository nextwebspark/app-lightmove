import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import type { ToastFn } from "../../../components/ui";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import { BILLING_KEY } from "../../billing/api/billingApi";
import { isBillingRefusal } from "../../billing/lib/billingView";
import type { Candidate } from "../../candidates/api/types";
import * as contactLookupApi from "../api/contactLookupApi";

/**
 * One channel's lookup. A missing LinkedIn profile is the one refusal that belongs in the section —
 * the field it names is right there — so it stays inline; running out of credits opens the out-of-credits
 * sheet, and every other failure toasts.
 */
export function useContactLookup(
  channel: "email" | "phone",
  projectId: string,
  candidateId: string,
  onSaved: (saved: Candidate) => void,
  toast: ToastFn,
) {
  const queryClient = useQueryClient();
  const [inlineError, setInlineError] = useState<string | null>(null);
  const lookup = useMutation({
    mutationFn: () =>
      channel === "email"
        ? contactLookupApi.findEmail(projectId, candidateId)
        : contactLookupApi.findPhone(projectId, candidateId),
    onSuccess: (result) => {
      onSaved(result.candidate);
      if (result.creditsSpent > 0) void queryClient.invalidateQueries({ queryKey: BILLING_KEY });
      if (result.outcome === "none") {
        toast(`No ${channel} on record for ${result.candidate.fullName}`);
      }
    },
    onError: (error) => {
      if (codeOf(error) === "CONTACT_LOOKUP_NO_PROFILE") {
        setInlineError(messageFor(error));
        return;
      }
      if (isBillingRefusal(error)) return;
      toast.error(messageFor(error));
    },
  });
  return {
    isPending: lookup.isPending,
    inlineError,
    find: () => {
      setInlineError(null);
      lookup.mutate();
    },
  };
}
