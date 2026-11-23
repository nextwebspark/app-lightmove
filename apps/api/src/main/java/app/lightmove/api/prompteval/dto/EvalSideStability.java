package app.lightmove.api.prompteval.dto;

/**
 * How far one version's answers to the same case moved between repeats. {@code sameAnswerRate} is the share, in
 * percent, of scored things (or of cases, for a prompt that scores nothing) answered the same every time;
 * {@code scoreSpread} is the mean standard deviation of a score. Each null where there is nothing to measure it on.
 */
public record EvalSideStability(Double sameAnswerRate, Double scoreSpread) {}
