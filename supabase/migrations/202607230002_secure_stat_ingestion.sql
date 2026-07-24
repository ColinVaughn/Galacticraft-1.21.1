create table if not exists public.global_stat_servers (
    id uuid primary key,
    name text not null check (char_length(name) between 1 and 80),
    public_key text not null check (char_length(public_key) between 40 and 256),
    status text not null default 'pending'
        check (status in ('pending', 'approved', 'quarantined', 'revoked')),
    official boolean not null default false,
    last_sequence bigint not null default 0 check (last_sequence >= 0),
    last_payload_hash text,
    last_generated_at timestamptz,
    last_received_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.global_stat_totals
    add column if not exists official_total bigint not null default 0
        check (official_total >= 0),
    add column if not exists community_total bigint not null default 0
        check (community_total >= 0);

create table if not exists public.global_stat_limits (
    stat_id text primary key references public.global_stat_totals(stat_id) on delete cascade,
    max_per_player_per_hour bigint not null check (max_per_player_per_hour > 0),
    burst_allowance bigint not null check (burst_allowance >= 0)
);

create table if not exists public.global_stat_server_totals (
    server_id uuid not null references public.global_stat_servers(id) on delete cascade,
    stat_id text not null references public.global_stat_totals(stat_id) on delete cascade,
    reported_total bigint not null check (reported_total >= 0),
    contributed_total bigint not null default 0 check (contributed_total >= 0),
    updated_at timestamptz not null default now(),
    primary key (server_id, stat_id)
);

create table if not exists public.global_stat_submissions (
    id uuid primary key default gen_random_uuid(),
    server_id uuid not null references public.global_stat_servers(id) on delete cascade,
    sequence bigint not null check (sequence > 0),
    generated_at timestamptz not null,
    received_at timestamptz not null default now(),
    session_id uuid not null,
    previous_hash text,
    payload_hash text not null,
    signature text not null,
    mod_version text not null,
    tracked_players integer not null check (tracked_players between 0 and 10000),
    online_players integer not null check (online_players between 0 and 10000),
    stats jsonb not null check (jsonb_typeof(stats) = 'object'),
    baseline boolean not null default false,
    unique (server_id, sequence),
    unique (server_id, payload_hash)
);

create table if not exists public.global_stat_rejections (
    id bigint generated always as identity primary key,
    server_id uuid references public.global_stat_servers(id) on delete set null,
    sequence bigint,
    payload_hash text,
    reason text not null check (char_length(reason) between 1 and 80),
    received_at timestamptz not null default now()
);

create index if not exists global_stat_submissions_server_received_idx
    on public.global_stat_submissions (server_id, received_at desc);
create index if not exists global_stat_rejections_server_received_idx
    on public.global_stat_rejections (server_id, received_at desc);
create index if not exists global_stat_rejections_throttle_idx
    on public.global_stat_rejections (server_id, reason, received_at desc);

alter table public.global_stat_servers enable row level security;
alter table public.global_stat_limits enable row level security;
alter table public.global_stat_server_totals enable row level security;
alter table public.global_stat_submissions enable row level security;
alter table public.global_stat_rejections enable row level security;

revoke all on table public.global_stat_servers from anon, authenticated;
revoke all on table public.global_stat_limits from anon, authenticated;
revoke all on table public.global_stat_server_totals from anon, authenticated;
revoke all on table public.global_stat_submissions from anon, authenticated;
revoke all on table public.global_stat_rejections from anon, authenticated;
revoke all on sequence public.global_stat_rejections_id_seq from anon, authenticated;

insert into public.global_stat_limits (stat_id, max_per_player_per_hour, burst_allowance)
values
    ('galacticraft:launch_rocket', 120, 20),
    ('galacticraft:safe_landing', 240, 30),
    ('galacticraft:crash_landing', 240, 30),
    ('galacticraft:open_parachest', 600, 50),
    ('galacticraft:interact_with_rocket_workbench', 1200, 100),
    ('galacticraft:clean_parachute', 240, 30),
    ('galacticraft:eat_cheese_wheel_slice', 3600, 200),
    ('galacticraft:cheese_cut', 1200, 100)
on conflict (stat_id) do update
set max_per_player_per_hour = excluded.max_per_player_per_hour,
    burst_allowance = excluded.burst_allowance;

create or replace function public.refresh_global_stat_totals()
returns void
language sql
security definer
set search_path = ''
as $$
    update public.global_stat_totals as totals
    set total = coalesce((
            select sum(server_totals.contributed_total)
            from public.global_stat_server_totals as server_totals
            join public.global_stat_servers as servers
              on servers.id = server_totals.server_id
            where server_totals.stat_id = totals.stat_id
              and servers.status = 'approved'
        ), 0),
        official_total = coalesce((
            select sum(server_totals.contributed_total)
            from public.global_stat_server_totals as server_totals
            join public.global_stat_servers as servers
              on servers.id = server_totals.server_id
            where server_totals.stat_id = totals.stat_id
              and servers.status = 'approved'
              and servers.official
        ), 0),
        community_total = coalesce((
            select sum(server_totals.contributed_total)
            from public.global_stat_server_totals as server_totals
            join public.global_stat_servers as servers
              on servers.id = server_totals.server_id
            where server_totals.stat_id = totals.stat_id
              and servers.status = 'approved'
              and not servers.official
        ), 0),
        updated_at = now();
$$;

create or replace function public.submit_global_statistics(
    p_server_id uuid,
    p_sequence bigint,
    p_generated_at timestamptz,
    p_session_id uuid,
    p_previous_hash text,
    p_payload_hash text,
    p_signature text,
    p_mod_version text,
    p_tracked_players integer,
    p_online_players integer,
    p_stats jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_server public.global_stat_servers%rowtype;
    v_existing_hash text;
    v_existing_baseline boolean;
    v_stat_id text;
    v_total numeric;
    v_previous_total bigint;
    v_delta numeric;
    v_max_delta numeric;
    v_elapsed_hours numeric;
    v_baseline boolean;
begin
    select *
      into v_server
      from public.global_stat_servers
     where id = p_server_id
     for update;

    if not found then
        return jsonb_build_object('accepted', false, 'reason', 'unknown_server');
    end if;

    select payload_hash, baseline
      into v_existing_hash, v_existing_baseline
      from public.global_stat_submissions
     where server_id = p_server_id
       and sequence = p_sequence;

    if found then
        if v_existing_hash = p_payload_hash then
            return jsonb_build_object(
                'accepted', true,
                'duplicate', true,
                'baseline', v_existing_baseline,
                'sequence', p_sequence,
                'payload_hash', p_payload_hash
            );
        end if;
        return jsonb_build_object('accepted', false, 'reason', 'sequence_reused');
    end if;

    if v_server.status <> 'approved' then
        return jsonb_build_object('accepted', false, 'reason', 'server_' || v_server.status);
    end if;
    if p_sequence <> v_server.last_sequence + 1 then
        return jsonb_build_object(
            'accepted', false,
            'reason', 'sequence_out_of_order',
            'expected_sequence', v_server.last_sequence + 1
        );
    end if;
    if p_previous_hash is distinct from v_server.last_payload_hash then
        return jsonb_build_object('accepted', false, 'reason', 'previous_hash_mismatch');
    end if;
    if v_server.last_generated_at is not null
       and p_generated_at <= v_server.last_generated_at then
        return jsonb_build_object('accepted', false, 'reason', 'timestamp_out_of_order');
    end if;
    if p_generated_at > clock_timestamp() + interval '5 minutes'
       or (v_server.last_sequence = 0
           and p_generated_at < clock_timestamp() - interval '5 minutes') then
        return jsonb_build_object('accepted', false, 'reason', 'timestamp_out_of_window');
    end if;
    if char_length(p_payload_hash) <> 43
       or char_length(p_signature) < 80
       or char_length(p_mod_version) not between 1 and 80 then
        return jsonb_build_object('accepted', false, 'reason', 'invalid_metadata');
    end if;
    if p_tracked_players < 0 or p_tracked_players > 10000
       or p_online_players < 0 or p_online_players > p_tracked_players then
        return jsonb_build_object('accepted', false, 'reason', 'invalid_player_counts');
    end if;
    if jsonb_typeof(p_stats) <> 'object'
       or (select count(*) from jsonb_object_keys(p_stats))
          <> (select count(*) from public.global_stat_limits)
       or exists (
            select 1
              from jsonb_each(p_stats) as entry
              left join public.global_stat_limits as limits on limits.stat_id = entry.key
             where limits.stat_id is null
                or jsonb_typeof(entry.value) <> 'string'
                or (entry.value #>> '{}') !~ '^[0-9]{1,19}$'
                or case
                    when jsonb_typeof(entry.value) = 'string'
                         and (entry.value #>> '{}') ~ '^[0-9]{1,19}$'
                        then (entry.value #>> '{}')::numeric > 9223372036854775807
                    else false
                   end
       ) then
        return jsonb_build_object('accepted', false, 'reason', 'invalid_statistics');
    end if;

    if v_server.last_received_at is not null
       and clock_timestamp() - v_server.last_received_at < interval '5 minutes' then
        return jsonb_build_object('accepted', false, 'reason', 'rate_limited');
    end if;

    v_baseline := v_server.last_sequence = 0;
    v_elapsed_hours := greatest(
        coalesce(extract(epoch from (p_generated_at - v_server.last_generated_at)) / 3600, 0),
        5.0 / 60.0
    );

    if not v_baseline then
        for v_stat_id, v_total in
            select entry.key, (entry.value #>> '{}')::numeric
              from jsonb_each(p_stats) as entry
        loop
            select reported_total
              into v_previous_total
              from public.global_stat_server_totals
             where server_id = p_server_id
               and stat_id = v_stat_id;

            if not found or v_total < v_previous_total then
                return jsonb_build_object('accepted', false, 'reason', 'statistics_decreased');
            end if;

            v_delta := v_total - v_previous_total;
            select limits.burst_allowance
                   + ceil(limits.max_per_player_per_hour
                          * greatest(p_online_players, 1)
                          * v_elapsed_hours)
              into v_max_delta
              from public.global_stat_limits as limits
             where limits.stat_id = v_stat_id;

            if v_delta > v_max_delta then
                return jsonb_build_object(
                    'accepted', false,
                    'reason', 'implausible_delta',
                    'stat_id', v_stat_id
                );
            end if;
        end loop;
    end if;

    for v_stat_id, v_total in
        select entry.key, (entry.value #>> '{}')::numeric
          from jsonb_each(p_stats) as entry
    loop
        insert into public.global_stat_server_totals (
            server_id, stat_id, reported_total, contributed_total, updated_at
        )
        values (
            p_server_id,
            v_stat_id,
            v_total::bigint,
            case when v_baseline then 0 else v_total::bigint end,
            clock_timestamp()
        )
        on conflict (server_id, stat_id) do update
        set reported_total = excluded.reported_total,
            contributed_total = public.global_stat_server_totals.contributed_total
                + (excluded.reported_total - public.global_stat_server_totals.reported_total),
            updated_at = excluded.updated_at;
    end loop;

    insert into public.global_stat_submissions (
        server_id, sequence, generated_at, session_id, previous_hash,
        payload_hash, signature, mod_version, tracked_players, online_players,
        stats, baseline
    )
    values (
        p_server_id, p_sequence, p_generated_at, p_session_id, p_previous_hash,
        p_payload_hash, p_signature, p_mod_version, p_tracked_players,
        p_online_players, p_stats, v_baseline
    );

    update public.global_stat_servers
       set last_sequence = p_sequence,
           last_payload_hash = p_payload_hash,
           last_generated_at = p_generated_at,
           last_received_at = clock_timestamp(),
           updated_at = clock_timestamp()
     where id = p_server_id;

    perform public.refresh_global_stat_totals();

    return jsonb_build_object(
        'accepted', true,
        'duplicate', false,
        'baseline', v_baseline,
        'sequence', p_sequence,
        'payload_hash', p_payload_hash
    );
end;
$$;

create or replace function public.record_global_statistics_rejection(
    p_server_id uuid,
    p_sequence bigint,
    p_payload_hash text,
    p_reason text
)
returns void
language sql
security definer
set search_path = ''
as $$
    insert into public.global_stat_rejections (server_id, sequence, payload_hash, reason)
    select
        p_server_id,
        p_sequence,
        left(p_payload_hash, 128),
        left(coalesce(nullif(p_reason, ''), 'unknown'), 80)
    where exists (
        select 1 from public.global_stat_servers where id = p_server_id
    )
      and not exists (
        select 1
          from public.global_stat_rejections
         where server_id = p_server_id
           and reason = left(coalesce(nullif(p_reason, ''), 'unknown'), 80)
           and received_at > clock_timestamp() - interval '1 minute'
    );
$$;

create or replace function public.refresh_global_stats_on_server_status()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if old.status is distinct from new.status then
        perform public.refresh_global_stat_totals();
    end if;
    return new;
end;
$$;

drop trigger if exists global_stats_server_status_changed on public.global_stat_servers;
create trigger global_stats_server_status_changed
after update of status on public.global_stat_servers
for each row execute function public.refresh_global_stats_on_server_status();

revoke all on function public.refresh_global_stat_totals() from public, anon, authenticated;
revoke all on function public.submit_global_statistics(
    uuid, bigint, timestamptz, uuid, text, text, text, text, integer, integer, jsonb
) from public, anon, authenticated;
revoke all on function public.record_global_statistics_rejection(
    uuid, bigint, text, text
) from public, anon, authenticated;
revoke all on function public.refresh_global_stats_on_server_status()
    from public, anon, authenticated;

grant execute on function public.refresh_global_stat_totals() to service_role;
grant execute on function public.submit_global_statistics(
    uuid, bigint, timestamptz, uuid, text, text, text, text, integer, integer, jsonb
) to service_role;
grant execute on function public.record_global_statistics_rejection(
    uuid, bigint, text, text
) to service_role;

comment on table public.global_stat_servers is
    'Enrolled Galacticraft servers and their Ed25519 public keys.';
comment on table public.global_stat_submissions is
    'Append-only log of signature-verified, accepted server statistic snapshots.';
comment on table public.global_stat_rejections is
    'Security audit log for rejected global statistic submissions.';
