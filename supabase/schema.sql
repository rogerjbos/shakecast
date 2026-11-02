-- Shakecast sync schema. Run once in the Supabase SQL editor.
-- Rows are scoped by a sync key the phones send in the x-sync-key header;
-- row level security makes each key see only its own rows.

create table if not exists public.subscriptions (
  sync_key      text   not null,
  feed_url      text   not null,
  title         text,
  author        text,
  itunes_id     bigint,
  artwork_url   text,
  subscribed    boolean not null default true,
  updated_at_ms bigint not null,
  primary key (sync_key, feed_url)
);

create table if not exists public.episode_states (
  sync_key      text   not null,
  feed_url      text   not null,
  guid          text   not null,
  position_ms   bigint not null default 0,
  is_played     boolean not null default false,
  played_at_ms  bigint,
  updated_at_ms bigint not null,
  primary key (sync_key, feed_url, guid)
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

alter table public.subscriptions  enable row level security;
alter table public.episode_states enable row level security;

drop policy if exists "own sync key" on public.subscriptions;
create policy "own sync key" on public.subscriptions
  for all to anon
  using      (sync_key = current_setting('request.headers', true)::json->>'x-sync-key')
  with check (sync_key = current_setting('request.headers', true)::json->>'x-sync-key');

drop policy if exists "own sync key" on public.episode_states;
create policy "own sync key" on public.episode_states
  for all to anon
  using      (sync_key = current_setting('request.headers', true)::json->>'x-sync-key')
  with check (sync_key = current_setting('request.headers', true)::json->>'x-sync-key');

grant select, insert, update on public.subscriptions  to anon;
grant select, insert, update on public.episode_states to anon;
