package app.lightmove.api.core.security.avatar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Off the sign-in's thread and after its commit: a failure leaves the photo as it was, never fails a sign-in. */
@Slf4j
@Component
@RequiredArgsConstructor
class ProviderAvatarCopier {

    private final UserAvatarService avatars;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onOffered(ProviderAvatarOffered offered) {
        try {
            avatars.copyFromProvider(offered.userId(), offered.source(), offered.pictureUrl());
        } catch (RuntimeException failed) {
            log.warn("Could not copy {}'s picture for user {}", offered.source(), offered.userId(), failed);
        }
    }
}
