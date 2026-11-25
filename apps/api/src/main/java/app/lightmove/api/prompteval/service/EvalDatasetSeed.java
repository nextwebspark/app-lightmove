package app.lightmove.api.prompteval.service;

import app.lightmove.api.prompteval.model.EvalSeedPack;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Records this build's synthetic sets as datasets at boot. A case is added once, by its key: one already there is
 * left as it is, retired or not, so a release never rewrites what a run scored or brings back what a person retired.
 * A pack's rubric becomes the dataset's next version when it differs from its newest.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvalDatasetSeed implements ApplicationRunner {

    private final EvalSeedPacks packs;
    private final EvalDatasetStore store;
    private final EvalRubricStore rubrics;
    private final TransactionTemplate transactions;

    @Override
    public void run(ApplicationArguments args) {
        try {
            seed(packs.all());
        } catch (RuntimeException failure) {
            log.error("Could not record this build's eval datasets; the next boot retries", failure);
        }
    }

    public void seed(List<EvalSeedPack> shipped) {
        transactions.executeWithoutResult(status -> {
            store.lockForSeed();
            for (EvalSeedPack pack : shipped) {
                UUID datasetId = store.ensureDataset(pack.promptId(), pack.name(), pack.description());
                store.add(datasetId, pack.cases(), null);
                if (!pack.rubric().isEmpty()) {
                    rubrics.ensure(datasetId, pack.rubric());
                }
            }
        });
    }
}
