-- Shakecast sync schema. Run in the Supabase SQL editor; safe to rerun after upgrades.
-- Every row belongs to a signed-in Supabase Auth user (auth.uid()); row level
-- security makes each user see only their own rows. Phones sign in with email
-- and password and send the user's access token as a bearer token.
--
-- Upgrading from the sync-key build: the old tables were keyed by a shared
-- x-sync-key header and are dropped below. Nothing is lost: after signing in
-- each phone pushes its whole library again, newest timestamp wins.

do $$
begin
  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'subscriptions' and column_name = 'sync_key'
  ) then
    drop table if exists public.subscriptions;
    drop table if exists public.episode_states;
    drop table if exists public.manual_skips;
    drop table if exists public.settings;
  end if;
end $$;

create table if not exists public.subscriptions (
  user_id        uuid    not null default auth.uid() references auth.users (id) on delete cascade,
  feed_url       text    not null,
  title          text,
  author         text,
  itunes_id      bigint,
  artwork_url    text,
  subscribed     boolean not null default true,
  intro_skip_sec integer,
  outro_skip_sec integer,
  updated_at_ms  bigint  not null,
  primary key (user_id, feed_url)
);

create table if not exists public.episode_states (
  user_id       uuid    not null default auth.uid() references auth.users (id) on delete cascade,
  feed_url      text    not null,
  guid          text    not null,
  position_ms   bigint  not null default 0,
  is_played     boolean not null default false,
  played_at_ms  bigint,
  updated_at_ms bigint  not null,
  primary key (user_id, feed_url, guid)
);

-- Spans the user skipped by hand, replayed automatically on every synced phone.
create table if not exists public.manual_skips (
  user_id       uuid    not null default auth.uid() references auth.users (id) on delete cascade,
  feed_url      text    not null,
  guid          text    not null,
  start_ms      bigint  not null,
  end_ms        bigint  not null,
  deleted       boolean not null default false,
  updated_at_ms bigint  not null,
  primary key (user_id, feed_url, guid, start_ms)
);

-- One JSON document of app settings per user.
create table if not exists public.settings (
  user_id       uuid   primary key default auth.uid() references auth.users (id) on delete cascade,
  settings      text   not null,
  updated_at_ms bigint not null
);

-- Last writer wins: an upsert carrying an older timestamp leaves the row alone.
create or replace function public.shakecast_keep_newest() returns trigger
language plpgsql as $$
begin
  if new.updated_at_ms < old.updated_at_ms then
    return null;
  end if;
  return new;
end;
$$;

drop trigger if exists subscriptions_keep_newest on public.subscriptions;
create trigger subscriptions_keep_newest
  before update on public.subscriptions
  for each row execute function public.shakecast_keep_newest();

drop trigger if exists episode_states_keep_newest on public.episode_states;
create trigger episode_states_keep_newest
  before update on public.episode_states
  for each row execute function public.shakecast_keep_newest();

drop trigger if exists manual_skips_keep_newest on public.manual_skips;
create trigger manual_skips_keep_newest
  before update on public.manual_skips
  for each row execute function public.shakecast_keep_newest();

drop trigger if exists settings_keep_newest on public.settings;
create trigger settings_keep_newest
  before update on public.settings
  for each row execute function public.shakecast_keep_newest();

alter table public.subscriptions  enable row level security;
alter table public.episode_states enable row level security;
alter table public.manual_skips   enable row level security;
alter table public.settings       enable row level security;

drop policy if exists "own rows" on public.subscriptions;
create policy "own rows" on public.subscriptions
  for all to authenticated
  using      (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

drop policy if exists "own rows" on public.episode_states;
create policy "own rows" on public.episode_states
  for all to authenticated
  using      (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

drop policy if exists "own rows" on public.manual_skips;
create policy "own rows" on public.manual_skips
  for all to authenticated
  using      (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

drop policy if exists "own rows" on public.settings;
create policy "own rows" on public.settings
  for all to authenticated
  using      (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

-- The anon role, which the app never uses once signed in, sees nothing.
revoke all on public.subscriptions  from anon;
revoke all on public.episode_states from anon;
revoke all on public.manual_skips   from anon;
revoke all on public.settings       from anon;

grant select, insert, update on public.subscriptions  to authenticated;
grant select, insert, update on public.episode_states to authenticated;
grant select, insert, update on public.manual_skips   to authenticated;
grant select, insert, update on public.settings       to authenticated;
