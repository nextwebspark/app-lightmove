import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import { useWorkspaceMode } from "../../workspace/lib/vocabulary";
import { BusinessUnitGlyph } from "./BusinessUnitGlyph";

/** An agency's client is a company and wears its logo; an in-house business unit wears the team glyph. */
export function ClientMark({ name, logoUrl, size }: { name: string; logoUrl: string | null; size: 26 | 32 }) {
  if (useWorkspaceMode() === "AGENCY") {
    return <CompanyLogo name={name} logo={logoUrl} size={size} />;
  }
  return <BusinessUnitGlyph size={size} />;
}
