package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Credentials and limits for Coresignal — {@code lightmove.vendor.coresignal.*}.
 *
 * <p>Coresignal is this application's source of <b>people</b>. The company universe is ours and stays
 * ours; Coresignal is handed a company we already hold and asked who works there.
 *
 * <p>Off by default so a fresh clone and the whole test suite run with no account and no cost — the
 * same reason {@code EmailSettings} lets the mail provider be a console printer.
 */
public record CoresignalSettings(
        @DefaultValue("false") boolean enabled,
        String apiKey,
        @DefaultValue("https://api.coresignal.com") String baseUrl,
        /** Their published per-key cap. Calls are paced to it rather than discovering it as 429s. */
        @DefaultValue("18") int requestsPerSecond
) {}
