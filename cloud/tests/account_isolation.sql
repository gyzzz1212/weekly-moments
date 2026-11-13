-- Run after the migration against a TEST project. Everything rolls back.
begin;
insert into auth.users(id,email) values
 ('11111111-1111-4111-8111-111111111111','account-a@example.invalid'),
 ('22222222-2222-4222-8222-222222222222','account-b@example.invalid');
set local role authenticated;
select set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',true);
do $$ declare body text := '{"format":"focus-space-backup","version":1,"payload":{"schema":1}}'; i int;
begin
 for i in 1..10 loop
  perform public.focus_save_backup(body,encode(sha256(convert_to(body,'UTF8')),'hex'),'test');
 end loop;
 if (select count(*) from public.focus_backups) <> 7 then raise exception 'Retention failed'; end if;
 begin
  insert into public.focus_backups(user_id,payload,sha256,client_version) values
   ('22222222-2222-4222-8222-222222222222',body,repeat('0',64),'test');
  raise exception 'Direct insert unexpectedly allowed';
 exception when insufficient_privilege then null; end;
 begin
  perform public.focus_save_backup(body,repeat('0',64),'test');
  raise exception 'Bad checksum accepted';
 exception when invalid_parameter_value then null; end;
end $$;
select set_config('request.jwt.claim.sub','22222222-2222-4222-8222-222222222222',true);
do $$ begin
 if (select count(*) from public.focus_backups) <> 0 then raise exception 'Cross-account read leaked records'; end if;
end $$;
reset role;
set local role anon;
select set_config('request.jwt.claim.sub','',true);
do $$ begin
 begin perform count(*) from public.focus_backups;raise exception 'Anonymous read unexpectedly allowed';
 exception when insufficient_privilege then null;end;
 begin perform public.focus_save_backup('{}',repeat('0',64),'test');raise exception 'Anonymous write unexpectedly allowed';
 exception when insufficient_privilege then null;end;
end $$;
reset role;
rollback;
