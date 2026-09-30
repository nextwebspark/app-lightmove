package app.lightmove.api.candidate.repository;

import app.lightmove.api.candidate.model.PersonActivity;
import org.springframework.data.jpa.repository.JpaRepository;

/** A person's history, written by {@code PersonActivityRecorder}; its reads arrive with the timeline. */
public interface PersonActivityRepository extends JpaRepository<PersonActivity, Long> {
}
