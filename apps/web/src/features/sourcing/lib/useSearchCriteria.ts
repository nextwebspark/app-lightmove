import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { useToast } from "../../../components/ui/Toast";
import { messageFor } from "../../../lib/errorCodes";
import { isBillingRefusal } from "../../billing/lib/billingView";
import * as sourcingApi from "../api/sourcingApi";
import type { SearchCriteria } from "../api/sourcingApi";

const SAVE_AFTER_MS = 600;

/**
 * The position's search as the panel edits it. Drafted by the AI the first time the panel opens with nothing saved,
 * then saved a moment after every edit; Reset drafts it again. {@code saveNow} writes a pending edit before a
 * search, which runs on the saved search.
 */
export function useSearchCriteria(projectId: string, companyIds: readonly string[], open: boolean) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const key = sourcingApi.sourcingKeys.criteria(projectId);
  const saved = useQuery({
    queryKey: key,
    queryFn: ({ signal }) => sourcingApi.getCriteria(projectId, signal),
    enabled: open,
  });
  const [draft, setDraft] = useState<SearchCriteria | null>(null);

  const suggest = useMutation({
    mutationFn: () => sourcingApi.suggestCriteria(projectId, [...companyIds]),
    onSuccess: (drafted) => {
      queryClient.setQueryData(key, drafted);
      setDraft(drafted.criteria);
    },
    onError: (error) => {
      if (!isBillingRefusal(error)) toast.error(messageFor(error));
    },
  });
  const save = useMutation({
    mutationFn: (criteria: SearchCriteria) => sourcingApi.saveCriteria(projectId, criteria),
    onSuccess: (written) => queryClient.setQueryData(key, written),
    onError: (error) => toast.error(messageFor(error)),
  });

  const savedCriteria = saved.data?.criteria ?? null;
  useEffect(() => {
    if (savedCriteria && draft === null) setDraft(savedCriteria);
  }, [savedCriteria, draft]);

  const drafted = useRef(false);
  const nothingSaved = open && saved.isSuccess && saved.data === null;
  useEffect(() => {
    if (!nothingSaved || drafted.current) return;
    drafted.current = true;
    suggest.mutate();
  }, [nothingSaved, suggest]);

  const pending = useRef<{ timer: number; criteria: SearchCriteria } | null>(null);
  useEffect(
    () => () => {
      if (pending.current) window.clearTimeout(pending.current.timer);
    },
    [],
  );

  const update = (next: SearchCriteria) => {
    setDraft(next);
    if (pending.current) window.clearTimeout(pending.current.timer);
    pending.current = {
      criteria: next,
      timer: window.setTimeout(() => {
        pending.current = null;
        save.mutate(next);
      }, SAVE_AFTER_MS),
    };
  };

  const saveNow = async () => {
    if (!pending.current) return;
    window.clearTimeout(pending.current.timer);
    const criteria = pending.current.criteria;
    pending.current = null;
    await save.mutateAsync(criteria);
  };

  return {
    criteria: draft,
    source: saved.data?.source ?? null,
    briefChanged: saved.data?.briefChanged ?? false,
    /** True while the AI drafts the search, or while it is still being read. */
    drafting: suggest.isPending || (open && saved.isPending),
    /** Nothing to show: the saved search could not be read, or the AI could not draft one. */
    draftFailed: draft === null && !suggest.isPending && (saved.isError || suggest.isError),
    /** Reads the saved search again when that failed, else asks the AI for its draft again. */
    retryDraft: () => (saved.isError ? void saved.refetch() : suggest.mutate()),
    update,
    reset: () => suggest.mutate(),
    saveNow,
  };
}
