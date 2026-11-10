import { request } from "../../../lib/apiClient";

/**
 * Whether this deployment offers the suppression list, erasure, restriction of processing and the candidate
 * retention review. A deployment fact, so it is read once.
 */
export const DATA_PROTECTION_CONFIG_KEY = ["dataProtection", "config"] as const;

export interface DataProtectionConfig {
  enabled: boolean;
}

export function getDataProtectionConfig(signal?: AbortSignal): Promise<DataProtectionConfig> {
  return request<DataProtectionConfig>("/data-protection/config", { signal });
}
