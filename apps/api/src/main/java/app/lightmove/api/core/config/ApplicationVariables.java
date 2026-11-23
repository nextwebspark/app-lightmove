package app.lightmove.api.core.config;

import app.lightmove.api.core.reload.PlatformConfigListener;
import app.lightmove.api.core.reload.ReloadableSnapshot;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalInt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What each application variable is right now: a super admin's override, else the yml value. Overrides are held in
 * memory until a platform change drops them. Never the reason a feature fails: with the table unreadable, or a
 * stored value that is off the allowlist or out of bounds, the yml value serves.
 */
@Slf4j
@Component
public class ApplicationVariables implements PlatformConfigListener {

    public static final String SCOPE = "settings";

    private static final Duration RETRY_AFTER_FAILED_READ = Duration.ofSeconds(30);

    private final LightMoveProperties properties;
    private final ApplicationVariableStore store;
    private final ReloadableSnapshot<Map<ApplicationVariable, Integer>> overrides;

    public ApplicationVariables(LightMoveProperties properties, ApplicationVariableStore store,
                                PlatformTransactionManager transactionManager, Clock clock) {
        this.properties = properties;
        this.store = store;
        // On its own connection: a failed statement aborts the Postgres transaction it ran in, so read on the
        // caller's it would serve the yml value into a request whose every later statement then fails (25P02).
        TransactionTemplate ownTransaction = new TransactionTemplate(transactionManager);
        ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        ownTransaction.setReadOnly(true);
        this.overrides = new ReloadableSnapshot<>("application variables", "configured values",
                () -> ownTransaction.execute(status -> storedOverrides()), Map.of(), RETRY_AFTER_FAILED_READ, clock);
    }

    public int intValue(ApplicationVariable variable) {
        Integer override = overrides.get().get(variable);
        return override != null ? override : variable.configuredDefault(properties);
    }

    /** The overrides as stored now, past the cache: what a screen that has just written one must show. */
    public Map<ApplicationVariable, Integer> storedOverrides() {
        Map<ApplicationVariable, Integer> usable = new EnumMap<>(ApplicationVariable.class);
        store.overrides().forEach((key, value) -> {
            ApplicationVariable variable = ApplicationVariable.ofKey(key).orElse(null);
            OptionalInt number = variable == null ? OptionalInt.empty() : variable.parseInRange(value);
            if (number.isPresent()) {
                usable.put(variable, number.getAsInt());
            } else {
                log.warn("Ignoring the stored application variable {}: off the allowlist or out of bounds", key);
            }
        });
        return usable;
    }

    @Override
    public String scope() {
        return SCOPE;
    }

    @Override
    public void reload() {
        overrides.drop();
    }
}
