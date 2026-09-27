package app.lightmove.api.assistant.model;

import app.lightmove.api.common.persona.model.HiringCompanyProfile;
import app.lightmove.api.workspace.model.Firm;

/**
 * Who a mandate is run by and for: the consultant's firm, and the company it hires for — the firm
 * itself when it hires in-house, the mandate's client when it is an agency.
 */
public record HiringSide(Firm firm, HiringCompanyProfile hiringCompany) {
}
