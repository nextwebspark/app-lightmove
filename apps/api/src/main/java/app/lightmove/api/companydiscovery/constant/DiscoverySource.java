package app.lightmove.api.companydiscovery.constant;

/**
 * Where a row's figures came from, which is the only thing the badge on the grid claims. It is
 * deliberately not also "is this already in the mandate" — those are orthogonal facts, and
 * conflating them is how a universe row a mandate already holds ends up badged as a web find.
 */
public enum DiscoverySource {

    /** Matched into the Apollo universe. The row is that market row, and it files by id. */
    UNIVERSE("universe"),

    /** No universe row, but a vendor holds its LinkedIn page. The row is the vendor's record. */
    RESEARCHED("researched"),

    /** Neither. The name, the page and the reason, and no figures at all. */
    WEB("web");

    private final String wireToken;

    DiscoverySource(String wireToken) {
        this.wireToken = wireToken;
    }

    public String value() {
        return wireToken;
    }
}
