/**
 * The HTTP contract for workspace onboarding and membership.
 *
 * <p>Split from the auth contract so a feature owns its own payloads; the auth {@code UserResponse}
 * still embeds {@link app.lightmove.api.workspace.dto.WorkspaceSummary}, which is the one place the
 * auth response reaches into workspace.
 */
package app.lightmove.api.workspace.dto;
