import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import {
  PLATFORM_PROMPTS_KEY,
  PLATFORM_SETTINGS_KEY,
  refreshPlatformConfig,
  type PlatformConfigPage,
} from "../api/platformConfigApi";
import type { PlatformConfigVersion } from "../api/types";

/**
 * The version every save raises and Refresh, which makes every server reload the overrides now rather than within
 * half a minute. The version shown is the one the server that answered has loaded; the two differ only briefly.
 */
export function ConfigRefreshButton({ page, config }: { page: PlatformConfigPage; config?: PlatformConfigVersion }) {
  const queryClient = useQueryClient();
  const toast = useToast();

  const refreshing = useMutation({
    mutationFn: () => refreshPlatformConfig(page),
    onSuccess: (version) => {
      toast(`Every server now runs version ${version.currentVersion}`);
      void queryClient.invalidateQueries({ queryKey: PLATFORM_SETTINGS_KEY });
      void queryClient.invalidateQueries({ queryKey: PLATFORM_PROMPTS_KEY });
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  return (
    <div className="flex items-center gap-3">
      {config && (
        <span
          className="font-mono text-meta text-u-text3"
          title={`Every save raises the version. This server loaded version ${config.loadedVersion}.`}
        >
          Version {config.currentVersion}
          {config.loadedVersion !== config.currentVersion && ` · this server on ${config.loadedVersion}`}
        </span>
      )}
      <Button variant="secondary" size="sm" loading={refreshing.isPending} onClick={() => refreshing.mutate()}>
        <Icon d={ICONS.refresh} size={14} />
        Refresh
      </Button>
    </div>
  );
}
