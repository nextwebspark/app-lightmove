package app.lightmove.api.assistant.tool;

import java.util.List;

/** One stage of the mandate: how many companies it holds, and the first of them in name order. */
public record MandateCompanies(String stage, long total, List<CompanyDetail> companies) {}
