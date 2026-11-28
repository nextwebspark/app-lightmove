# Flyway with the repository's migrations baked in, run as a Cloud Run job inside the VPC (deploy.yml, "Migrate the
# database (Cloud Run job inside the VPC)"). The connection comes from FLYWAY_URL, FLYWAY_USER and FLYWAY_PASSWORD.
# The session raises app.rls_maintenance because row-level security is forced on the tables the migrate role owns (V184).
FROM redgate/flyway:11
COPY apps/api/src/main/resources/db/migration /flyway/sql
CMD ["-baselineOnMigrate=true", "-placeholders.iam_user=", "-initSql=SET app.rls_maintenance = 'on'", "-connectRetries=5", "migrate"]
