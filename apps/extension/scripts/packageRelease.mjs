/**
 * Builds a publishable extension and zips it for the Chrome Web Store.
 *
 * Exists because three things are easy to get wrong by hand, and each fails silently:
 *
 *   1. Building without LM_WORKSPACE_ORIGIN, which bakes a placeholder host into `host_permissions`
 *      and produces an extension that cannot reach any workspace. Refused here rather than warned
 *      about — a plain `npm run build` may legitimately use the placeholder (CI does), but a release
 *      may not.
 *   2. Leaving `key` in the uploaded manifest. It fixes the id for unpacked loading and has no meaning
 *      to the store, which assigns its own. Stripped from the zip, and left in `dist/` so local
 *      loading still works.
 *   3. Zipping the folder rather than its contents. The store expects the manifest at the root of the
 *      archive and rejects an archive with a directory wrapping it.
 */
import { execFileSync } from "node:child_process";
import { globSync, readFileSync, writeFileSync, rmSync, mkdirSync, cpSync } from "node:fs";
import { resolve } from "node:path";

const workspaceOrigin = process.env.LM_WORKSPACE_ORIGIN?.trim();
if (!workspaceOrigin) {
  console.error(
    "\n\x1b[31m  ✗ LM_WORKSPACE_ORIGIN is not set.\x1b[0m\n" +
      "    A release must name the workspace it talks to — the manifest asks Chrome for permission on\n" +
      "    that exact host, and it cannot be changed after the fact.\n\n" +
      "    LM_WORKSPACE_ORIGIN=https://your-service.run.app npm run build:release\n",
  );
  process.exit(1);
}

const run = (command, args, cwd) =>
  execFileSync(command, args, {
    stdio: "inherit",
    cwd,
    env: { ...process.env, LM_WORKSPACE_ORIGIN: workspaceOrigin },
  });

run("npm", ["run", "build"]);

// Packaged from a copy, so dist/ keeps its `key` and stays loadable unpacked after a release build.
const packageDir = resolve("release/package");
rmSync(resolve("release"), { recursive: true, force: true });
mkdirSync(packageDir, { recursive: true });
cpSync(resolve("dist"), packageDir, { recursive: true });

const manifestPath = resolve(packageDir, "manifest.json");
const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
delete manifest.key;
writeFileSync(manifestPath, JSON.stringify(manifest, null, 2));

// Source maps are for debugging our own build, not for the listing — and they carry the full original
// sources, which there is no reason to ship. Every one of them, including the chunks'.
for (const map of globSync("**/*.map", { cwd: packageDir })) {
  rmSync(resolve(packageDir, map), { force: true });
}

const zipPath = resolve(`release/lightmove-capture-${manifest.version}.zip`);
run("zip", ["-qr", zipPath, ".", "-x", ".*"], packageDir);

console.log(
  `\n\x1b[32m  ✓ ${zipPath}\x1b[0m\n` +
    `    Built against ${workspaceOrigin}\n\n` +
    "    Upload it at https://chrome.google.com/webstore/devconsole — then take the id the store\n" +
    "    assigns and set it as EXTENSION_ID (deploy) and VITE_EXTENSION_ID (web build), or the API\n" +
    "    will refuse the extension's requests by CORS.\n",
);
