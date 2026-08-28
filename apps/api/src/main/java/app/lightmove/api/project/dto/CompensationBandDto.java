package app.lightmove.api.project.dto;

/**
 * The band the brief asks for, <b>always annualised</b>. Either bound may be null; the pair is absent
 * when both are.
 *
 * <p>A brief may quote its base monthly (V39's {@code base_salary_mode}), which is ordinary in the
 * GCC. A market report states an annual figure by convention, and a band whose period the reader has
 * to guess is worse than no band — so the conversion happens before the figures get here.
 */
public record CompensationBandDto(Long min, Long max, String currency) {}
