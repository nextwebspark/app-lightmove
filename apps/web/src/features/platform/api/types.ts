/** Mirrors the API's `PlatformSettingGroup`: the area of the product a variable tunes. */
export type PlatformSettingGroup = "SOURCING" | "FAIR_USE" | "TRIAL" | "EXPORT" | "MCP" | "ASSISTANT";

/** Mirrors the API's `PlatformSettingType`: how a value is written and bounded. */
export type PlatformSettingType = "INT" | "LONG" | "DOUBLE" | "DURATION" | "MODEL_NAME";

/** The stored version every save raises, against the one the instance that answered has loaded. */
export interface PlatformConfigVersion {
  currentVersion: number;
  loadedVersion: number;
  loadedAt: string | null;
}

export interface PlatformSetting {
  key: string;
  group: PlatformSettingGroup;
  type: PlatformSettingType;
  label: string;
  description: string;
  /** Days for a duration; both zero for a model name. */
  min: number;
  max: number;
  defaultValue: string;
  value: string;
  overridden: boolean;
  updatedByName: string | null;
  updatedAt: string | null;
}

export interface PlatformSettingsResponse {
  settings: PlatformSetting[];
  config: PlatformConfigVersion;
}

export interface PlatformPromptSummary {
  id: string;
  label: string;
  overridden: boolean;
  activeVersion: number | null;
  variables: string[];
  updatedByName: string | null;
  updatedAt: string | null;
}

export interface PlatformPromptsResponse {
  prompts: PlatformPromptSummary[];
  config: PlatformConfigVersion;
}

export interface PlatformPromptVersion {
  version: number;
  active: boolean;
  createdByName: string | null;
  createdAt: string;
}

export interface PlatformPromptDetail {
  id: string;
  label: string;
  resource: string;
  variables: string[];
  shippedBody: string;
  /** Null while the shipped prompt is in use. */
  activeBody: string | null;
  activeVersion: number | null;
  /** Newest first. */
  versions: PlatformPromptVersion[];
  config: PlatformConfigVersion;
}
