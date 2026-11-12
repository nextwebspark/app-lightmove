import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useToast } from "../../../components/ui/Toast";
import { messageFor } from "../../../lib/errorCodes";
import * as customColumnsApi from "../api/customColumnsApi";
import type { CustomColumn } from "../api/types";

/**
 * Removes a mandate's own column. The definition goes and the values stay on the rows, so the toast's Undo defines it
 * again under the same name, which brings them back.
 */
export function useDeleteCustomColumn(projectId: string) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const refresh = () =>
    void queryClient.invalidateQueries({ queryKey: customColumnsApi.CUSTOM_COLUMNS_KEY(projectId) });

  const restore = useMutation({
    mutationFn: (column: CustomColumn) =>
      customColumnsApi.defineCustomColumn(projectId, {
        target: column.target,
        label: column.label,
        dataType: column.dataType,
      }),
    onSuccess: (_restored, column) => {
      refresh();
      toast(`${column.label} is back.`);
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  return useMutation({
    mutationFn: (column: CustomColumn) => customColumnsApi.deleteCustomColumn(projectId, column.id),
    onSuccess: (_result, column) => {
      refresh();
      toast.success("Column removed. The values stay on the rows.", { undo: () => restore.mutate(column) });
    },
    onError: (error) => toast.error(messageFor(error)),
  });
}
