import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useToast } from "../../../components/ui/Toast";
import { messageFor } from "../../../lib/errorCodes";
import { useProjectRowsChanged } from "../../../lib/projectRows";
import * as sourcingApi from "../api/sourcingApi";
import { peopleOf } from "./sourcingSummary";

/** What each company's latest search found, and Add, which files the ticked people from what was read. */
export function useSuggestions(projectId: string, open: boolean) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const rowsChanged = useProjectRowsChanged();
  const key = sourcingApi.sourcingKeys.suggestions(projectId);

  const suggestions = useQuery({
    queryKey: key,
    queryFn: ({ signal }) => sourcingApi.getSuggestions(projectId, signal),
    enabled: open,
  });

  const add = useMutation({
    mutationFn: (people: { triageCompanyId: string; linkedinSlug: string }[]) =>
      sourcingApi.addSuggested(projectId, people),
    onSuccess: (added) => {
      void queryClient.invalidateQueries({ queryKey: key });
      void rowsChanged(projectId);
      const parts = [added.added > 0 ? `Added ${peopleOf(added.added)} to In universe` : "Nobody was added"];
      if (added.alreadyOnPosition > 0) parts.push(`${added.alreadyOnPosition} already on this position`);
      if (added.expired > 0) parts.push(`${added.expired} expired — search again`);
      if (added.withheld > 0) parts.push(`${added.withheld} on the suppression list or restricted`);
      if (added.added > 0) toast.success(parts.join(" · "));
      else toast(parts.join(" · "));
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  return {
    companies: suggestions.data?.companies ?? [],
    loading: suggestions.isPending && open,
    refetch: suggestions.refetch,
    add: add.mutate,
    adding: add.isPending,
  };
}
