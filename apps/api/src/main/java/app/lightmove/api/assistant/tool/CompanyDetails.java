package app.lightmove.api.assistant.tool;

import java.util.List;

/** The companies asked about, in the order asked, and the keys nothing is known about. */
public record CompanyDetails(List<CompanyDetail> companies, List<String> notFound) {}
