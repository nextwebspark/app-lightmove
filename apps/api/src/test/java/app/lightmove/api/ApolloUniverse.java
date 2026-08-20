package app.lightmove.api;

import java.sql.Array;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Seeds {@code app_lm_apollo_companies} for a test.
 *
 * <p>The universe is ETL reference data — the pipeline writes it and the application only reads —
 * so a fresh schema leaves the table empty and every test that reads it has to put its own rows in.
 * A shared helper rather than a copy of the same INSERT in five files: the table has 46 columns and
 * a test that cares about two of them should say so and let the rest default.
 *
 * <p>{@code reset()} first, always. Testcontainers reuses one database across the suite, so a test
 * that skipped it would count another test's companies and pass or fail depending on ordering.
 */
public final class ApolloUniverse {

    private final JdbcTemplate db;

    public ApolloUniverse(JdbcTemplate db) {
        this.db = db;
    }

    /** Empty the universe. Call from {@code @BeforeEach} in any test that reads it. */
    public void reset() {
        db.execute("DELETE FROM app_lm_apollo_companies");
    }

    /** A company to be filled in and inserted. */
    public Seed company(String apolloAccountId, String companyName) {
        return new Seed(db, apolloAccountId, companyName);
    }

    /** Fluent because most tests set two fields and mean "anything" for the other forty-four. */
    public static final class Seed {

        private final JdbcTemplate db;
        private final String apolloAccountId;
        private final String companyName;
        private String industry;
        private String companyCountry;
        private String companyCity;
        private Integer numEmployees;
        private Long annualRevenue;
        private String website;
        private String logoUrl;
        private String shortDescription;
        private Short foundedYear;
        private final List<String> keywords = new ArrayList<>();

        private Seed(JdbcTemplate db, String apolloAccountId, String companyName) {
            this.db = db;
            this.apolloAccountId = apolloAccountId;
            this.companyName = companyName;
        }

        public Seed industry(String value) {
            this.industry = value;
            return this;
        }

        public Seed country(String value) {
            this.companyCountry = value;
            return this;
        }

        public Seed city(String value) {
            this.companyCity = value;
            return this;
        }

        public Seed employees(Integer value) {
            this.numEmployees = value;
            return this;
        }

        /** Null is meaningful here — nine rows in ten carry no revenue, and tests need that case. */
        public Seed revenue(Long value) {
            this.annualRevenue = value;
            return this;
        }

        public Seed website(String value) {
            this.website = value;
            return this;
        }

        public Seed logo(String value) {
            this.logoUrl = value;
            return this;
        }

        public Seed description(String value) {
            this.shortDescription = value;
            return this;
        }

        public Seed founded(int value) {
            this.foundedYear = (short) value;
            return this;
        }

        /** Market segments are matched through these — lower-case, as the real table stores them. */
        public Seed keywords(String... values) {
            this.keywords.addAll(List.of(values));
            return this;
        }

        public void insert() {
            db.update(connection -> {
                var statement = connection.prepareStatement("""
                        INSERT INTO app_lm_apollo_companies
                            (apollo_account_id, company_name, industry, company_country, company_city,
                             num_employees, annual_revenue, website, logo_url, short_description,
                             founded_year, keywords, row_hash)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """);
                Array keywordArray = connection.createArrayOf("text", keywords.toArray());
                statement.setString(1, apolloAccountId);
                statement.setString(2, companyName);
                statement.setString(3, industry);
                statement.setString(4, companyCountry);
                statement.setString(5, companyCity);
                setIntOrNull(statement, 6, numEmployees);
                setLongOrNull(statement, 7, annualRevenue);
                statement.setString(8, website);
                statement.setString(9, logoUrl);
                statement.setString(10, shortDescription);
                if (foundedYear == null) {
                    statement.setNull(11, java.sql.Types.SMALLINT);
                } else {
                    statement.setShort(11, foundedYear);
                }
                statement.setArray(12, keywordArray);
                // row_hash is the loader's change detector and NOT NULL; nothing here reads it.
                statement.setString(13, apolloAccountId);
                return statement;
            });
        }

        private static void setIntOrNull(java.sql.PreparedStatement statement, int index, Integer value)
                throws java.sql.SQLException {
            if (value == null) {
                statement.setNull(index, java.sql.Types.INTEGER);
            } else {
                statement.setInt(index, value);
            }
        }

        private static void setLongOrNull(java.sql.PreparedStatement statement, int index, Long value)
                throws java.sql.SQLException {
            if (value == null) {
                statement.setNull(index, java.sql.Types.BIGINT);
            } else {
                statement.setLong(index, value);
            }
        }
    }
}
