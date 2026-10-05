import { useQueryClient } from "@tanstack/react-query";
import { useCallback } from "react";
import * as poolApi from "../api/poolApi";
import type { PersonRecord } from "../api/types";

/** Takes a person record a write answered with, and refreshes everything else that reads that person. */
export function usePersonRecordUpdate() {
  const queryClient = useQueryClient();
  return useCallback(
    (updated: PersonRecord) => {
      queryClient.setQueryData(poolApi.PERSON_RECORD_KEY(updated.personId), updated);
      for (const queryKey of poolApi.personChangedKeys(updated.personId).slice(1)) {
        void queryClient.invalidateQueries({ queryKey });
      }
    },
    [queryClient],
  );
}
