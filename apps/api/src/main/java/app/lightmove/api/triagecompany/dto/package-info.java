/**
 * The HTTP contract for a mandate's triaged companies. The two creating writes have opposite trust
 * models: {@link app.lightmove.api.triagecompany.dto.AddTriageCompanyRequest} names an id only,
 * {@link app.lightmove.api.triagecompany.dto.CaptureCompanyRequest} carries the fields.
 */
package app.lightmove.api.triagecompany.dto;
