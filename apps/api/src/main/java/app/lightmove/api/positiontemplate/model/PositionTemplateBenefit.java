package app.lightmove.api.positiontemplate.model;

import app.lightmove.api.common.constant.BenefitFrequency;
import org.springframework.modulith.NamedInterface;

/**
 * One allowance line a template drafts — the name and the period it is paid over, never an amount. A
 * template knows that a GCC package carries a housing allowance; what the client will pay for it is a
 * fact about the mandate, and a figure nobody gave us is worse than the empty field beside the line.
 */
@NamedInterface(name = "api", propagate = false)
public record PositionTemplateBenefit(String name, BenefitFrequency frequency) {
}
