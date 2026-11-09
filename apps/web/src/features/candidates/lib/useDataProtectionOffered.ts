import { useQuery } from "@tanstack/react-query";
import * as dataProtectionApi from "../api/dataProtectionApi";

/** False until the read answers, and on a failed one: a control the server would refuse is never offered. */
export function useDataProtectionOffered(): boolean {
  const config = useQuery({
    queryKey: dataProtectionApi.DATA_PROTECTION_CONFIG_KEY,
    queryFn: ({ signal }) => dataProtectionApi.getDataProtectionConfig(signal),
    staleTime: Infinity,
  });
  return config.data?.enabled === true;
}
