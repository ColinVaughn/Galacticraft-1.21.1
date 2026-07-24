create table if not exists public.global_stat_totals (
    stat_id text primary key,
    total bigint not null default 0 check (total >= 0),
    updated_at timestamptz not null default now()
);

alter table public.global_stat_totals enable row level security;

revoke all on table public.global_stat_totals from anon, authenticated;
grant select on table public.global_stat_totals to anon, authenticated;

create policy "Global statistics are publicly readable"
    on public.global_stat_totals
    for select
    to anon, authenticated
    using (true);

insert into public.global_stat_totals (stat_id, total)
values
    ('galacticraft:launch_rocket', 0),
    ('galacticraft:safe_landing', 0),
    ('galacticraft:crash_landing', 0),
    ('galacticraft:open_parachest', 0),
    ('galacticraft:interact_with_rocket_workbench', 0),
    ('galacticraft:clean_parachute', 0),
    ('galacticraft:eat_cheese_wheel_slice', 0),
    ('galacticraft:cheese_cut', 0)
on conflict (stat_id) do nothing;

comment on table public.global_stat_totals is
    'Public read model for Galacticraft global statistics. Updates must come from a trusted backend or Edge Function.';
