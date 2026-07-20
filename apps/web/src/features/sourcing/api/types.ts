/** One company matching the project's saved Strategy scope. */
export interface CompanyResult {
  id: number;
  name: string;
  domain: string | null;
  sector: string | null;
  employeeRange: string | null;
  revenueRange: string | null;
  location: string;
}

export interface SourcingResponse {
  companies: CompanyResult[];
  totalCount: number;
  page: number;
  size: number;
}
