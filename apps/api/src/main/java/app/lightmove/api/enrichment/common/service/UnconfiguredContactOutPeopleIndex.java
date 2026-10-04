package app.lightmove.api.enrichment.common.service;

import app.lightmove.api.enrichment.common.model.ContactOutCount;
import app.lightmove.api.enrichment.common.service.ContactOutPeopleRecords.ContactOutSearchAnswer;
import java.util.Map;

/** No ContactOut key on this deployment: nothing is offered, and asking anyway is a wiring mistake. */
public class UnconfiguredContactOutPeopleIndex implements ContactOutPeopleIndex {

    @Override
    public boolean isOffered() {
        return false;
    }

    @Override
    public ContactOutCount count(Map<String, Object> filter) {
        throw new IllegalStateException("ContactOut is not configured on this deployment");
    }

    @Override
    public ContactOutSearchAnswer search(Map<String, Object> filter, int page, int pageSize) {
        throw new IllegalStateException("ContactOut is not configured on this deployment");
    }
}
