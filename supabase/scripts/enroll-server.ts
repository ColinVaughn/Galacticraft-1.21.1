function argument(name: string): string | null {
  const index = Deno.args.indexOf(name);
  return index >= 0 ? Deno.args[index + 1] ?? null : null;
}

const url = Deno.env.get("SUPABASE_URL")?.replace(/\/$/, "");
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
const serverId = argument("--server-id");
const publicKey = argument("--public-key");
const name = argument("--name");
const official = Deno.args.includes("--official");

if (!url || !serviceRoleKey || !serverId || !publicKey || !name) {
  console.error(
    "Usage: deno run --allow-env --allow-net enroll-server.ts " +
      "--server-id UUID --public-key BASE64 --name NAME [--official]",
  );
  console.error("SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY must be set.");
  Deno.exit(2);
}

const response = await fetch(`${url}/rest/v1/global_stat_servers`, {
  method: "POST",
  headers: {
    apikey: serviceRoleKey,
    authorization: `Bearer ${serviceRoleKey}`,
    "content-type": "application/json",
    prefer: "return=representation",
  },
  body: JSON.stringify({
    id: serverId,
    name,
    public_key: publicKey,
    status: "approved",
    official,
  }),
});

if (!response.ok) {
  console.error(
    `Enrollment failed (${response.status}): ${await response.text()}`,
  );
  Deno.exit(1);
}
console.log(`Enrolled ${name} (${serverId})${official ? " as official" : ""}.`);
