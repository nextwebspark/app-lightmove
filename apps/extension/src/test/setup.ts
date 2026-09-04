import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// Unmounts between tests. The panel hooks register `chrome.*` listeners on mount, so a component left
// standing keeps answering events fired by the next test.
afterEach(cleanup);
