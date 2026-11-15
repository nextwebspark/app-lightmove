import { z } from "zod";

// Zod probes for eval with `new Function`; under the SPA's CSP (`script-src 'self'`, #929) the probe is refused and
// reported as a violation on every page, though zod falls back. Jitless skips the probe; the parse is the same.
z.config({ jitless: true });
