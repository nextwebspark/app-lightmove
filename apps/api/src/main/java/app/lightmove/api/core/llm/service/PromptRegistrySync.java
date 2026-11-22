package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.constant.PromptOrigin;
import app.lightmove.api.core.llm.model.BundledPrompt;
import app.lightmove.api.core.llm.model.ProductionVersion;
import app.lightmove.api.core.reload.PlatformConfigReload;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Records this build's prompts in the registry at boot. A text not seen before becomes a new {@code BUNDLED}
 * version; production follows the build unless a version edited in the console is live, which stays live. This is
 * the record only: {@link RegistryPromptSource} serves a shipped text from the build itself, so the revisions of a
 * rollout moving the one label between them change what no instance answers with.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptRegistrySync implements ApplicationRunner {

    static final String SHIPPED = "Shipped with a release";

    private final BundledPrompts bundled;
    private final PromptRegistryStore store;
    private final TransactionTemplate transactions;
    private final PlatformConfigReload reload;

    @Override
    public void run(ApplicationArguments args) {
        try {
            sync(bundled.all());
        } catch (RuntimeException failure) {
            log.error("Could not record this build's prompts in the registry; the bundled files serve wherever "
                    + "it holds no version, and the next boot retries", failure);
        }
    }

    public void sync(List<BundledPrompt> prompts) {
        transactions.executeWithoutResult(status -> {
            store.lockForSync();
            boolean changed = false;
            for (BundledPrompt prompt : prompts) {
                changed |= record(prompt);
            }
            // An instance that served before this sync holds these texts without their version numbers.
            if (changed) {
                reload.bump(RegistryPromptSource.SCOPE);
            }
        });
    }

    private boolean record(BundledPrompt prompt) {
        Optional<Integer> recorded = store.bundledVersion(prompt);
        int version = recorded.orElseGet(() -> store.insertBundled(prompt));
        Optional<ProductionVersion> production = store.productionOf(prompt.promptId());
        if (production.isEmpty()) {
            store.moveProduction(prompt.promptId(), null, version, null, SHIPPED);
            return true;
        }
        // To this build's text, not the newest bundled row: a rollback deploy must serve the older prompt again.
        ProductionVersion live = production.get();
        if (live.origin() == PromptOrigin.BUNDLED && live.version() != version) {
            store.moveProduction(prompt.promptId(), live.version(), version, null, SHIPPED);
            return true;
        }
        return recorded.isEmpty();
    }
}
