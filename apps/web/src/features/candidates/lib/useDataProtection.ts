import { useQuery } from "@tanstack/react-query";
import * as dataProtectionApi from "../api/dataProtectionApi";

export interface DataProtection {
  /** False while the read is pending and on a failed one: a control the server would refuse is never offered. */
  offered: boolean;
  isPending: boolean;
}

export function useDataProtection(): DataProtection {
  const config = useQuery({
    queryKey: dataProtectionApi.DATA_PROTECTION_CONFIG_KEY,
    queryFn: ({ signal }) => dataProtectionApi.getDataProtectionConfig(signal),
    staleTime: Infinity,
  });
  return { offered: config.data?.enabled === true, isPending: config.isPending };
}
