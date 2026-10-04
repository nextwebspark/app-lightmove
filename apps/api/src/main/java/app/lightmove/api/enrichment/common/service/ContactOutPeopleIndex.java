package app.lightmove.api.enrichment.common.service;

import app.lightmove.api.enrichment.common.model.ContactOutCount;
import app.lightmove.api.enrichment.common.service.ContactOutPeopleRecords.ContactOutSearchAnswer;
import java.util.Map;

/**
 * ContactOut's people index on the deployment's key: {@link ContactOutPeopleClient} where one is set,
 * {@link UnconfiguredContactOutPeopleIndex} where none is, so a consumer asks {@link #isOffered()} rather
 * than holding a bean that may be absent.
 */
public interface ContactOutPeopleIndex {

    boolean isOffered();

    /** Free; how many profiles match {@code filter} and how many of them ContactOut holds a contact for. */
    ContactOutCount count(Map<String, Object> filter);

    /** Bills one search credit for every profile the page returns. */
    ContactOutSearchAnswer search(Map<String, Object> filter, int page, int pageSize);
}
