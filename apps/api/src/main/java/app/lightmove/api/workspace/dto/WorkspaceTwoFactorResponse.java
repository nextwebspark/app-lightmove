package app.lightmove.api.workspace.dto;

import java.util.List;

/**
 * Settings → Security's admin card: the workspace's requirement and who has a factor, pure clients included, since
 * the requirement covers them too.
 *
 * @param offered whether the deployment offers two-factor at all; while it does not, nothing is enforced
 */
public record WorkspaceTwoFactorResponse(boolean offered, boolean required, List<MemberTwoFactorResponse> members) {}
