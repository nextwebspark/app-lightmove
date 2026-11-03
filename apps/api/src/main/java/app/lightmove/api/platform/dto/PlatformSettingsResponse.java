package app.lightmove.api.platform.dto;

import java.util.List;

public record PlatformSettingsResponse(List<PlatformSettingResponse> settings, PlatformConfigVersionResponse config) {}
