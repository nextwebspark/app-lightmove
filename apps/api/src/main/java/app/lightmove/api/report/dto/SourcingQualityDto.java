package app.lightmove.api.report.dto;

import app.lightmove.api.report.constant.SourcingQualityLevel;

/**
 * How complete a set of executives is: the share with any contact on file, with a verified one, and
 * with a base salary recorded. {@code level} is {@code GOOD} where the three average half or more.
 */
public record SourcingQualityDto(int contactPct, int verifiedPct, int compPct, SourcingQualityLevel level) {}
