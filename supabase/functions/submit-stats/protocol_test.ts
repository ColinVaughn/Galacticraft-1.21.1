import { assertEquals } from "jsr:@std/assert@1";
import { canonicalPayload, validateSubmission } from "./protocol.ts";

const submission = {
  version: 1 as const,
  serverId: "123e4567-e89b-42d3-a456-426614174000",
  sequence: 7,
  generatedAt: Math.floor(Date.now() / 1000),
  sessionId: "123e4567-e89b-42d3-a456-426614174001",
  previousHash: null,
  modVersion: "5.4.6",
  trackedPlayers: 2,
  onlinePlayers: 1,
  stats: {
    "galacticraft:cheese_cut": "8",
    "galacticraft:clean_parachute": "7",
    "galacticraft:crash_landing": "6",
    "galacticraft:eat_cheese_wheel_slice": "5",
    "galacticraft:interact_with_rocket_workbench": "4",
    "galacticraft:launch_rocket": "3",
    "galacticraft:open_parachest": "2",
    "galacticraft:safe_landing": "1",
  },
  payloadHash: "a".repeat(43),
  signature: "b".repeat(86),
};

Deno.test("validates and canonicalizes a submission deterministically", () => {
  assertEquals(validateSubmission(submission), null);
  assertEquals(
    canonicalPayload(submission),
    `v1
123e4567-e89b-42d3-a456-426614174000
7
${submission.generatedAt}
123e4567-e89b-42d3-a456-426614174001

5.4.6
2
1
galacticraft:cheese_cut=8
galacticraft:clean_parachute=7
galacticraft:crash_landing=6
galacticraft:eat_cheese_wheel_slice=5
galacticraft:interact_with_rocket_workbench=4
galacticraft:launch_rocket=3
galacticraft:open_parachest=2
galacticraft:safe_landing=1
`,
  );
});

Deno.test("rejects omitted and unknown statistic keys", () => {
  const stats: Record<string, string> = structuredClone(submission.stats);
  delete stats["galacticraft:safe_landing"];
  stats["other:fake"] = "9000";
  const changed = { ...submission, stats };
  assertEquals(validateSubmission(changed), "invalid_statistics");
});
