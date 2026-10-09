package app.lightmove.api;

import app.lightmove.api.billing.credit.model.ContactCreditThresholdCrossed;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.event.TransactionalEventListener;

/** Every contact-credit threshold announcement that reached a listener after its spend committed. */
public class RecordingCreditThresholds {

    private final List<ContactCreditThresholdCrossed> crossed = new CopyOnWriteArrayList<>();

    @TransactionalEventListener
    void on(ContactCreditThresholdCrossed event) {
        crossed.add(event);
    }

    public List<ContactCreditThresholdCrossed> of(UUID workspaceId) {
        return crossed.stream().filter(event -> event.workspaceId().equals(workspaceId)).toList();
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        RecordingCreditThresholds recordingCreditThresholds() {
            return new RecordingCreditThresholds();
        }
    }
}
