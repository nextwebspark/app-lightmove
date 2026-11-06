package app.lightmove.api.core.persistence.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Work that must commit on its own: a write made from {@code afterCommit}, where the finished transaction's resources
 * are still bound and an ordinary template would join it and write nothing.
 */
@Component
public class RequiresNewTransactions {

    private final TransactionTemplate template;

    public RequiresNewTransactions(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
        this.template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void run(Runnable work) {
        template.executeWithoutResult(status -> work.run());
    }
}
