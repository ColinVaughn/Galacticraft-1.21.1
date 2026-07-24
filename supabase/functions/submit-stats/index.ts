import { createClient } from "jsr:@supabase/supabase-js@2";
import {
  canonicalPayload,
  constantTimeEquals,
  decodeBase64,
  encodeBase64Url,
  StatisticsSubmission,
  validateSubmission,
} from "./protocol.ts";

const MAX_BODY_BYTES = 32 * 1024;
const encoder = new TextEncoder();

function json(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

async function readBody(request: Request): Promise<string | null> {
  if (request.body === null) return "";
  const reader = request.body.getReader();
  const decoder = new TextDecoder();
  let size = 0;
  let body = "";
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > MAX_BODY_BYTES) {
      await reader.cancel();
      return null;
    }
    body += decoder.decode(value, { stream: true });
  }
  return body + decoder.decode();
}

Deno.serve(async (request) => {
  if (request.method !== "POST") {
    return json({ accepted: false, reason: "method_not_allowed" }, 405);
  }

  const declaredLength = Number(request.headers.get("content-length") ?? 0);
  if (declaredLength > MAX_BODY_BYTES) {
    return json({ accepted: false, reason: "payload_too_large" }, 413);
  }

  const rawBody = await readBody(request);
  if (rawBody === null) {
    return json({ accepted: false, reason: "payload_too_large" }, 413);
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(rawBody);
  } catch {
    return json({ accepted: false, reason: "invalid_json" }, 400);
  }

  const validationError = validateSubmission(parsed);
  if (validationError !== null) {
    return json({ accepted: false, reason: validationError }, 422);
  }
  const submission = parsed as StatisticsSubmission;

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceRoleKey) {
    console.error("Required Supabase function secrets are unavailable.");
    return json({ accepted: false, reason: "service_unavailable" }, 503);
  }
  const supabase = createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const recordRejection = async (reason: string): Promise<void> => {
    const { error } = await supabase.rpc("record_global_statistics_rejection", {
      p_server_id: submission.serverId,
      p_sequence: submission.sequence,
      p_payload_hash: submission.payloadHash,
      p_reason: reason,
    });
    if (error) console.error("Unable to record rejected submission.", error);
  };

  const canonical = canonicalPayload(submission);
  const calculatedHash = encodeBase64Url(
    await crypto.subtle.digest("SHA-256", encoder.encode(canonical)),
  );
  if (!constantTimeEquals(calculatedHash, submission.payloadHash)) {
    await recordRejection("payload_hash_mismatch");
    return json({ accepted: false, reason: "payload_hash_mismatch" }, 422);
  }

  const { data: server, error: serverError } = await supabase
    .from("global_stat_servers")
    .select("public_key,status")
    .eq("id", submission.serverId)
    .maybeSingle();
  if (serverError) {
    console.error("Unable to load submitting server.", serverError);
    return json({ accepted: false, reason: "service_unavailable" }, 503);
  }
  if (!server) {
    return json({ accepted: false, reason: "unknown_server" }, 403);
  }
  if (server.status !== "approved") {
    await recordRejection(`server_${server.status}`);
    return json({ accepted: false, reason: `server_${server.status}` }, 403);
  }

  let signatureValid = false;
  try {
    const publicKey = await crypto.subtle.importKey(
      "spki",
      decodeBase64(server.public_key),
      { name: "Ed25519" },
      false,
      ["verify"],
    );
    signatureValid = await crypto.subtle.verify(
      "Ed25519",
      publicKey,
      decodeBase64(submission.signature),
      encoder.encode(canonical),
    );
  } catch (error) {
    console.error("Unable to verify an enrolled server public key.", error);
  }
  if (!signatureValid) {
    await recordRejection("invalid_signature");
    return json({ accepted: false, reason: "invalid_signature" }, 403);
  }

  const { data, error } = await supabase.rpc("submit_global_statistics", {
    p_server_id: submission.serverId,
    p_sequence: submission.sequence,
    p_generated_at: new Date(submission.generatedAt * 1000).toISOString(),
    p_session_id: submission.sessionId,
    p_previous_hash: submission.previousHash,
    p_payload_hash: submission.payloadHash,
    p_signature: submission.signature,
    p_mod_version: submission.modVersion,
    p_tracked_players: submission.trackedPlayers,
    p_online_players: submission.onlinePlayers,
    p_stats: submission.stats,
  });
  if (error) {
    console.error("Statistic ingestion transaction failed.", error);
    return json({ accepted: false, reason: "service_unavailable" }, 503);
  }

  const result = data as Record<string, unknown>;
  if (result.accepted !== true) {
    const reason = typeof result.reason === "string"
      ? result.reason
      : "rejected";
    await recordRejection(reason);
    return json(result, reason === "rate_limited" ? 429 : 409);
  }
  return json(result);
});
