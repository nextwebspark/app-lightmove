package app.lightmove.api.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.constant.ContactStopKind;
import app.lightmove.api.candidate.constant.TimelineGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContactStopTimelineTest {

    @Test
    @DisplayName("every door a stop comes through is a person-level line under Contacts, read on every position")
    void everyDoorIsPersonLevel() {
        for (ContactStopKind door : ContactStopKind.values()) {
            assertThat(PersonTimelineService.PERSON_LEVEL_KINDS).as(door.name()).contains(door.activityKind());
            assertThat(TimelineGroup.CONTACTS.kinds()).as(door.name()).contains(door.activityKind());
        }
    }
}
