const allowed = new Set(["pending", "approved", "quarantined", "revoked"]);
function argument(name: string): string | null {
  const index = Deno.args.indexOf(name);
  return index >= 0 ? Deno.args[index + 1] ?? null : null;
}

const url = Deno.env.get("SUPABASE_URL")?.replace(/\/$/, "");
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
const serverId = argument("--server-id");
const status = argument("--status");

if (!url || !serviceRoleKey || !serverId || !status || !allowed.has(status)) {
  console.error(
    "Usage: deno run --allow-env --allow-net set-server-status.ts " +
      "--server-id UUID --status pending|approved|quarantined|revoked",
  );
  Deno.exit(2);
}

const response = await fetch(
  `${url}/rest/v1/global_stat_servers?id=eq.${encodeURIComponent(serverId)}`,
  {
    method: "PATCH",
    headers: {
      apikey: serviceRoleKey,
      authorization: `Bearer ${serviceRoleKey}`,
      "content-type": "application/json",
      prefer: "return=minimal",
    },
    body: JSON.stringify({ status }),
  },
);

if (!response.ok) {
  console.error(
    `Status update failed (${response.status}): ${await response.text()}`,
  );
  Deno.exit(1);
}
console.log(`Set ${serverId} to ${status}.`);
