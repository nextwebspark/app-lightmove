import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { askServiceWorker } from "../../background/extensionMessages";
import { DEFAULT_CAPTURE_SETTINGS, type CaptureSettings } from "../../domain/captureSettings";

const SETTINGS_KEY = ["extension", "settings"] as const;

export function useCaptureSettings() {
  const queryClient = useQueryClient();

  const settings = useQuery<CaptureSettings>({
    queryKey: SETTINGS_KEY,
    queryFn: async () => {
      const result = await askServiceWorker({ kind: "readSettings" });
      return result.ok ? result.value : DEFAULT_CAPTURE_SETTINGS;
    },
  });

  const save = useMutation({
    mutationFn: async (changed: Partial<CaptureSettings>) => {
      const result = await askServiceWorker({ kind: "writeSettings", settings: changed });
      if (!result.ok) {
        throw new Error(result.message);
      }
      return result.value;
    },
    onSuccess: (saved) => queryClient.setQueryData(SETTINGS_KEY, saved),
  });

  return {
    settings: settings.data ?? DEFAULT_CAPTURE_SETTINGS,
    isLoading: settings.isPending,
    update: save.mutate,
  };
}
