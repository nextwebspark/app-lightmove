package app.lightmove.api.common.persona.model;

/** The company a mandate hires for, as the assistant is told about it. Any part may be null. */
public record HiringCompanyProfile(String name, String industry, String city, String country, String website,
                                   Integer employees, HiringPersona persona) {

    /**
     * A business unit read against its parent firm: what it left blank of its place and persona lists is the firm's,
     * and so is its industry while it names no sectors of its own. Its website and headcount are never the firm's.
     */
    public HiringCompanyProfile within(HiringCompanyProfile parent) {
        if (parent == null) {
            return this;
        }
        HiringPersona own = persona == null ? HiringPersona.empty() : persona;
        // City and country are one place: half of each would put the firm's city in the unit's country.
        boolean placed = city != null || country != null;
        // A unit naming its own sectors chose another market: the firm's industry would contradict them.
        boolean ownMarket = industry != null || !own.sectors().isEmpty();
        return new HiringCompanyProfile(name,
                ownMarket ? industry : parent.industry(),
                placed ? city : parent.city(),
                placed ? country : parent.country(),
                website, employees, own.fallingBackTo(parent.persona()));
    }
}
