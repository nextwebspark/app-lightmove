package app.lightmove.api.project.dto;

import java.util.List;

/**
 * The HTTP contract for a project's Sourcing results: the company universe filtered by that project's
 * saved Strategy scope (sectors + company size), one page at a time.
 */
public final class SourcingDtos {

    private SourcingDtos() {
    }

    /** One matching company, projected down to what the Sourcing list shows. */
    public record CompanyResultDto(long id, String name, String domain, String sector,
                                    String employeeRange, String revenueRange, String location) {}

    public record SourcingResponse(List<CompanyResultDto> companies, long totalCount, int page, int size) {}
}
