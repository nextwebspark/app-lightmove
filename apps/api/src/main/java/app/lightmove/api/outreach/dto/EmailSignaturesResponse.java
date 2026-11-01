package app.lightmove.api.outreach.dto;

import java.util.List;

/** The caller's signatures in this workspace, oldest first, what they follow, and how many may be kept. */
public record EmailSignaturesResponse(List<EmailSignatureResponse> signatures, SignatureSourcesResponse sources,
                                      int limit) {}
