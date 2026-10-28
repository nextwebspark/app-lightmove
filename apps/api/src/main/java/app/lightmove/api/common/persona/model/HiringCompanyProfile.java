package app.lightmove.api.common.persona.model;

/** The company a mandate hires for, as the assistant is told about it. Any part may be null. */
public record HiringCompanyProfile(String name, String industry, String city, String country, String website,
                                   Integer employees, HiringPersona persona) {

    /**
     * A business unit read against its parent firm: what it left blank of its industry, place and persona lists is
     * the firm's. Its website and headcount are never the firm's — those describe the group, told apart.
     */
    public HiringCompanyProfile within(HiringCompanyProfile parent) {
        if (parent == null) {
            return this;
        }
        HiringPersona own = persona == null ? HiringPersona.empty() : persona;
        // City and country are one place: half of each would put the firm's city in the unit's country.
        boolean placed = city != null || country != null;
        return new HiringCompanyProfile(name,
                industry != null ? industry : parent.industry(),
                placed ? city : parent.city(),
                placed ? country : parent.country(),
                website, employees, own.fallingBackTo(parent.persona()));
    }
}
