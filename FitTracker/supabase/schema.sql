-- FitTracker tables for Supabase.
-- Paste this whole file into Supabase Dashboard -> SQL Editor -> New query, then click Run.
-- Row Level Security makes sure each user can only read and change their own rows.

create table if not exists public.profiles (
    id          uuid primary key references auth.users (id) on delete cascade,
    name        text    not null default 'Fit User',
    age         integer not null default 21,
    height_cm   real    not null default 170,
    weight_kg   real    not null default 65,
    step_goal   integer not null default 8000,
    heart_goal  integer not null default 30,
    updated_at  timestamptz not null default now()
);

create table if not exists public.workouts (
    id            bigint generated always as identity primary key,
    user_id       uuid    not null default auth.uid() references auth.users (id) on delete cascade,
    type          text    not null,
    start_time    bigint  not null,          -- milliseconds since 1970 (same as the app)
    duration_sec  bigint  not null,
    distance_m    double precision not null default 0,
    calories      double precision not null default 0,
    heart_points  integer not null default 0,
    steps         integer not null default 0,
    unique (user_id, start_time)
);

create table if not exists public.daily_steps (
    user_id  uuid    not null default auth.uid() references auth.users (id) on delete cascade,
    day      text    not null,               -- yyyy-MM-dd
    steps    integer not null,
    primary key (user_id, day)
);

alter table public.profiles    enable row level security;
alter table public.workouts    enable row level security;
alter table public.daily_steps enable row level security;

drop policy if exists "own profile" on public.profiles;
create policy "own profile" on public.profiles
    for all using (auth.uid() = id) with check (auth.uid() = id);

drop policy if exists "own workouts" on public.workouts;
create policy "own workouts" on public.workouts
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists "own steps" on public.daily_steps;
create policy "own steps" on public.daily_steps
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
