function encodeBase64(value: ArrayBuffer): string {
  const bytes = new Uint8Array(value);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

const keyPair = await crypto.subtle.generateKey(
  "Ed25519",
  true,
  ["sign", "verify"],
) as CryptoKeyPair;
const privateKey = encodeBase64(
  await crypto.subtle.exportKey("pkcs8", keyPair.privateKey),
);
const publicKey = encodeBase64(
  await crypto.subtle.exportKey("spki", keyPair.publicKey),
);

console.log(`Server ID:  ${crypto.randomUUID()}`);
console.log(`Private key: ${privateKey}`);
console.log(`Public key:  ${publicKey}`);
console.log("");
console.log("Keep the private key only on the Minecraft server.");
