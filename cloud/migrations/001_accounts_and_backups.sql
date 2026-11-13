-- Execute once in the project's SQL Editor. Supabase Auth owns email/passwords.
begin;
create table public.focus_backups (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    created_at timestamptz not null default now(),
    payload text not null check (octet_length(payload) <= 16777216),
    sha256 text not null check (sha256 ~ '^[a-f0-9]{64}$'),
    client_version varchar(32) not null
);
create index focus_backups_owner_date on public.focus_backups(user_id, created_at desc, id desc);
alter table public.focus_backups enable row level security;
revoke all on public.focus_backups from anon, authenticated;
grant select on public.focus_backups to authenticated;
create policy focus_backups_read_own on public.focus_backups for select to authenticated
    using ((select auth.uid()) = user_id);

-- All writes go through this function: ownership comes only from the authenticated JWT,
-- and the server serializes per-user writes and retains exactly the newest seven snapshots.
create function public.focus_save_backup(p_payload text, p_sha256 text, p_version text)
returns uuid language plpgsql security definer set search_path = '' as $$
declare
    owner_id uuid := auth.uid();
    backup_id uuid;
    document jsonb;
begin
    if owner_id is null then raise exception 'Sign in required' using errcode = '42501'; end if;
    if p_payload is null or octet_length(p_payload) > 16777216 or length(p_version) > 32 or p_version is null then
        raise exception 'Invalid backup size or version' using errcode = '22023';
    end if;
    if p_sha256 is null or p_sha256 <> encode(sha256(convert_to(p_payload, 'UTF8')), 'hex') then
        raise exception 'Checksum mismatch' using errcode = '22023';
    end if;
    document := p_payload::jsonb;
    if document->>'format' is distinct from 'focus-space-backup'
        or document->>'version' is distinct from '1'
        or document->'payload'->>'schema' is distinct from '1' then
        raise exception 'Invalid backup format' using errcode = '22023';
    end if;
    perform pg_advisory_xact_lock(hashtextextended(owner_id::text, 0));
    insert into public.focus_backups(user_id, payload, sha256, client_version)
        values (owner_id, p_payload, p_sha256, p_version) returning id into backup_id;
    delete from public.focus_backups where user_id = owner_id and id not in (
        select id from public.focus_backups where user_id = owner_id order by created_at desc, id desc limit 7
    );
    return backup_id;
end;
$$;
revoke all on function public.focus_save_backup(text,text,text) from public, anon;
grant execute on function public.focus_save_backup(text,text,text) to authenticated;
commit;
