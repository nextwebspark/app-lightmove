// What an authenticator app shows for a two-factor setup key right now: node totp.mjs <base32 secret>
// RFC 6238 as the API checks it (core/security/mfa/Totp): HMAC-SHA1, 30-second steps, six digits.
import { createHmac } from "node:crypto";

const ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

function base32Decode(secret) {
  let bits = "";
  for (const letter of secret.replace(/=+$/, "").toUpperCase()) {
    const value = ALPHABET.indexOf(letter);
    if (value < 0) throw new Error("not a base32 secret");
    bits += value.toString(2).padStart(5, "0");
  }
  const bytes = [];
  for (let at = 0; at + 8 <= bits.length; at += 8) bytes.push(parseInt(bits.slice(at, at + 8), 2));
  return Buffer.from(bytes);
}

const counter = Buffer.alloc(8);
counter.writeBigUInt64BE(BigInt(Math.floor(Date.now() / 1000 / 30)));
const digest = createHmac("sha1", base32Decode(process.argv[2])).update(counter).digest();
const offset = digest[digest.length - 1] & 0x0f;
console.log(String((digest.readUInt32BE(offset) & 0x7fffffff) % 1_000_000).padStart(6, "0"));
