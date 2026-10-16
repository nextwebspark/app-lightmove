/**
 * The HTTP contract for a mandate's triaged companies. The two creating writes have opposite trust
 * models: {@link app.uncava.api.triagecompany.dto.AddTriageCompanyRequest} names an id only,
 * {@link app.uncava.api.triagecompany.dto.CaptureCompanyRequest} carries the fields.
 */
package app.uncava.api.triagecompany.dto;
