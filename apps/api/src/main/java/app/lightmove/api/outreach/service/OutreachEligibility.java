package app.lightmove.api.outreach.service;

import app.lightmove.api.candidate.model.OutreachRecipient;
import app.lightmove.api.outreach.constant.EnrollmentStatus;
import app.lightmove.api.outreach.model.OutreachEnrollment;
import app.lightmove.api.outreach.model.RecipientEligibility;
import app.lightmove.api.outreach.repository.OutreachEnrollmentRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Reads what {@link RecipientEligibility} needs beyond the people themselves: who is already in a live sequence. */
@Component
@RequiredArgsConstructor
class OutreachEligibility {

    private final OutreachEnrollmentRepository enrollments;

    RecipientEligibility of(UUID projectId, Collection<OutreachRecipient> recipients) {
        List<UUID> personIds = recipients.stream().map(OutreachRecipient::personId).distinct().toList();
        if (personIds.isEmpty()) {
            return new RecipientEligibility(Map.of());
        }
        return new RecipientEligibility(enrollments
                .findByProjectIdAndPersonIdInAndStatusIn(projectId, personIds, EnrollmentStatus.LIVE).stream()
                .collect(Collectors.toMap(OutreachEnrollment::getPersonId, Function.identity(),
                        (first, second) -> first)));
    }
}
