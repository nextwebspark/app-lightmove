import { useMemo } from "react";
import guide from "../../../../../../docs/mcp.md?raw";
import { publicGuide } from "../lib/publicGuide";
import { GuideMarkdown } from "./GuideMarkdown";

export default function McpGuide() {
  const markdown = useMemo(() => publicGuide(guide, window.location.origin), []);
  return <GuideMarkdown markdown={markdown} />;
}
