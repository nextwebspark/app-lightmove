/**
 * The HTTP contract for workspace onboarding and membership. The auth {@code UserResponse} embeds
 * {@link app.lightmove.api.core.security.dto.WorkspaceSummary}, its one reach into workspace.
 */
@NamedInterface(name = "api", propagate = false)
package app.lightmove.api.workspace.dto;

import org.springframework.modulith.NamedInterface;
