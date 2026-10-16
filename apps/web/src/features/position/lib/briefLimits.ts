/**
 * The brief's server limits (`PutPositionDetailsRequest`, `PutCompensationRequest`, `OrgNodeDto`, `CompetencyDto`,
 * `CriterionRequest`), mirrored so a screen stops typing or adding where the server would refuse the whole step.
 */
export const BRIEF_LIMITS = {
  roleTitle: 160,
  department: 160,
  locationCity: 120,
  narrative: 4000,
  responsibility: 200,
  responsibilities: 20,
  seatTitle: 160,
  seatName: 160,
  benefitName: 120,
  benefits: 20,
  incentiveVesting: 200,
  competencyName: 120,
  competencyDescription: 300,
  criterion: 300,
  criteria: 30,
  /** `uncava.position.document.max-file-size-bytes`'s default. */
  documentBytes: 10 * 1024 * 1024,
} as const;
