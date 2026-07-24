export const ALLOWED_STATS = [
  "galacticraft:cheese_cut",
  "galacticraft:clean_parachute",
  "galacticraft:crash_landing",
  "galacticraft:eat_cheese_wheel_slice",
  "galacticraft:interact_with_rocket_workbench",
  "galacticraft:launch_rocket",
  "galacticraft:open_parachest",
  "galacticraft:safe_landing",
] as const;

export interface StatisticsSubmission {
  version: 1;
  serverId: string;
  sequence: number;
  generatedAt: number;
  sessionId: string;
  previousHash: string | null;
  modVersion: string;
  trackedPlayers: number;
  onlinePlayers: number;
  stats: Record<string, string>;
  payloadHash: string;
  signature: string;
}

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const BASE64_URL_PATTERN = /^[A-Za-z0-9_-]+$/;
const UNSIGNED_LONG_PATTERN = /^[0-9]{1,19}$/;
const MAX_SIGNED_LONG = 9_223_372_036_854_775_807n;

export function validateSubmission(value: unknown): string | null {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    return "invalid_body";
  }

  const submission = value as Partial<StatisticsSubmission>;
  if (submission.version !== 1) return "unsupported_version";
  if (
    typeof submission.serverId !== "string" ||
    !UUID_PATTERN.test(submission.serverId)
  ) return "invalid_server_id";
  if (!Number.isSafeInteger(submission.sequence) || submission.sequence! <= 0) {
    return "invalid_sequence";
  }
  const now = Math.floor(Date.now() / 1000);
  if (
    !Number.isSafeInteger(submission.generatedAt) ||
    submission.generatedAt! > now + 300
  ) {
    return "timestamp_out_of_window";
  }
  if (
    typeof submission.sessionId !== "string" ||
    !UUID_PATTERN.test(submission.sessionId)
  ) return "invalid_session_id";
  if (
    submission.previousHash !== null &&
    (typeof submission.previousHash !== "string" ||
      submission.previousHash.length !== 43 ||
      !BASE64_URL_PATTERN.test(submission.previousHash))
  ) {
    return "invalid_previous_hash";
  }
  if (
    typeof submission.modVersion !== "string" ||
    submission.modVersion.length < 1 || submission.modVersion.length > 80
  ) {
    return "invalid_mod_version";
  }
  if (
    !Number.isSafeInteger(submission.trackedPlayers) ||
    submission.trackedPlayers! < 0 || submission.trackedPlayers! > 10_000 ||
    !Number.isSafeInteger(submission.onlinePlayers) ||
    submission.onlinePlayers! < 0 ||
    submission.onlinePlayers! > submission.trackedPlayers!
  ) {
    return "invalid_player_counts";
  }
  if (
    typeof submission.payloadHash !== "string" ||
    submission.payloadHash.length !== 43 ||
    !BASE64_URL_PATTERN.test(submission.payloadHash)
  ) {
    return "invalid_payload_hash";
  }
  if (
    typeof submission.signature !== "string" ||
    submission.signature.length !== 86 ||
    !BASE64_URL_PATTERN.test(submission.signature)
  ) {
    return "invalid_signature";
  }
  if (
    submission.stats === null || typeof submission.stats !== "object" ||
    Array.isArray(submission.stats)
  ) return "invalid_statistics";

  const keys = Object.keys(submission.stats).sort();
  if (
    keys.length !== ALLOWED_STATS.length ||
    keys.some((key, index) => key !== ALLOWED_STATS[index])
  ) {
    return "invalid_statistics";
  }
  for (const stat of ALLOWED_STATS) {
    const total = submission.stats[stat];
    if (
      typeof total !== "string" || !UNSIGNED_LONG_PATTERN.test(total) ||
      BigInt(total) > MAX_SIGNED_LONG
    ) return "invalid_statistics";
  }
  return null;
}

export function canonicalPayload(submission: StatisticsSubmission): string {
  const lines = [
    "v1",
    submission.serverId,
    String(submission.sequence),
    String(submission.generatedAt),
    submission.sessionId,
    submission.previousHash ?? "",
    submission.modVersion,
    String(submission.trackedPlayers),
    String(submission.onlinePlayers),
  ];
  for (const stat of ALLOWED_STATS) {
    lines.push(`${stat}=${submission.stats[stat]}`);
  }
  return `${lines.join("\n")}\n`;
}

export function decodeBase64(value: string): Uint8Array {
  const standard = value.replaceAll("-", "+").replaceAll("_", "/");
  const padded = standard + "=".repeat((4 - standard.length % 4) % 4);
  return Uint8Array.from(atob(padded), (character) => character.charCodeAt(0));
}

export function encodeBase64Url(value: ArrayBuffer): string {
  const bytes = new Uint8Array(value);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replaceAll(
    "=",
    "",
  );
}

export function constantTimeEquals(left: string, right: string): boolean {
  const length = Math.max(left.length, right.length);
  let difference = left.length ^ right.length;
  for (let index = 0; index < length; index++) {
    difference |= (left.charCodeAt(index) || 0) ^
      (right.charCodeAt(index) || 0);
  }
  return difference === 0;
}
