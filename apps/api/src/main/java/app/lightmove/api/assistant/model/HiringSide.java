package app.lightmove.api.assistant.model;

import app.lightmove.api.common.persona.model.HiringCompanyProfile;
import app.lightmove.api.workspace.model.Firm;

/**
 * Who a mandate is run by and for: the consultant's firm, and the company it hires for — the mandate's client at an
 * agency; in-house, the mandate's business unit once it has a persona of its own, with the firm as its
 * {@code parentFirm}, and otherwise the firm itself with no parent.
 */
public record HiringSide(Firm firm, HiringCompanyProfile hiringCompany, HiringCompanyProfile parentFirm) {
}
