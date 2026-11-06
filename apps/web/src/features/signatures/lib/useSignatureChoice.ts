import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import * as signaturesApi from "../api/signaturesApi";
import type { EmailSignature } from "../api/signaturesApi";

export interface SignatureChoice {
  /** The sender's signatures, the default first; empty while loading or where they keep none. */
  signatures: EmailSignature[];
  isLoaded: boolean;
  /** Whether the email carries one: ticked by default where a default exists. */
  included: boolean;
  chosen: EmailSignature | null;
  setIncluded: (included: boolean) => void;
  choose: (id: string) => void;
  /** What the send asks for: null sends none. */
  signatureId: string | null;
}

/**
 * Which of the sender's signatures an email carries: their default, ticked, until they untick it or pick another. A
 * person who keeps signatures but no default starts unticked, and ticking picks their first.
 */
export function useSignatureChoice(): SignatureChoice {
  const query = useQuery({
    queryKey: signaturesApi.SIGNATURES_KEY,
    queryFn: ({ signal }) => signaturesApi.listSignatures(signal),
    staleTime: 60_000,
  });
  const [includedChoice, setIncludedChoice] = useState<boolean | null>(null);
  const [chosenId, setChosenId] = useState<string | null>(null);

  const signatures = [...(query.data?.signatures ?? [])].sort((a, b) => Number(b.isDefault) - Number(a.isDefault));
  const defaultSignature = signatures.find((signature) => signature.isDefault) ?? null;
  const chosen = signatures.find((signature) => signature.id === chosenId) ?? defaultSignature ?? signatures[0] ?? null;
  const included = chosen !== null && (includedChoice ?? defaultSignature !== null);

  return {
    signatures,
    isLoaded: query.isSuccess,
    included,
    chosen,
    setIncluded: setIncludedChoice,
    choose: (id) => {
      setChosenId(id);
      setIncludedChoice(true);
    },
    signatureId: included && chosen ? chosen.id : null,
  };
}
