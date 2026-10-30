// The six-digit code an authenticator app would show for a Base32 secret (RFC 6238: SHA-1, 30-second steps).
// Usage: node totp.mjs SECRET [STEP_OFFSET]   — a step offset lets a script use a step not yet spent.
import { createHmac } from "node:crypto";

const [secret, offset = "0"] = process.argv.slice(2);
if (!secret) {
  console.error("usage: node totp.mjs SECRET [STEP_OFFSET]");
  process.exit(2);
}

const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
let bits = "";
for (const char of secret.replace(/=+$/, "").toUpperCase()) {
  const value = alphabet.indexOf(char);
  if (value < 0) throw new Error(`not Base32: ${char}`);
  bits += value.toString(2).padStart(5, "0");
}
const key = Buffer.from(bits.match(/.{8}/g).map((byte) => parseInt(byte, 2)));

const step = BigInt(Math.floor(Date.now() / 1000 / 30) + Number(offset));
const counter = Buffer.alloc(8);
counter.writeBigUInt64BE(step);

const digest = createHmac("sha1", key).update(counter).digest();
const at = digest[digest.length - 1] & 0x0f;
const code = (digest.readUInt32BE(at) & 0x7fffffff) % 1_000_000;
process.stdout.write(String(code).padStart(6, "0"));
