-- Run against the seeded local database; all writes are rolled back.
-- Paper isolation, write restrictions and trigger behaviour from 20261023000000_review_fixes.sql.
begin;

-- An outsider (Eve) in her own paper, with a story, message and attachment there.
insert into auth.users (id, instance_id, aud, role, email, raw_user_meta_data, created_at, updated_at)
values ('00000000-0000-0000-0000-0000000000e6', '00000000-0000-0000-0000-000000000000', 'authenticated',
        'authenticated', 'eve@example.com', '{"display_name":"%"}', now(), now());
insert into public.groups (id, name, created_by)
  values ('00000000-0000-0000-0000-0000000000a2', 'Elsewhere', '00000000-0000-0000-0000-0000000000e6');
insert into public.group_members (group_id, user_id, role)
  values ('00000000-0000-0000-0000-0000000000a2', '00000000-0000-0000-0000-0000000000e6', 'owner');
insert into public.stories (id, group_id, title, summary)
  values ('00000000-0000-0000-0000-0000000000b9', '00000000-0000-0000-0000-0000000000a2', 'Private', 'Private');
insert into public.messages (id, story_id, author_id, body)
  values ('00000000-0000-0000-0000-0000000000d9', '00000000-0000-0000-0000-0000000000b9', '00000000-0000-0000-0000-0000000000e6', 'secret');
-- a member of The Grapevine whose display name is a LIKE wildcard
update public.profiles set display_name = '%' where id = '00000000-0000-0000-0000-000000000004';
insert into storage.buckets (id, name, public) values ('media', 'media', false) on conflict do nothing;
insert into storage.objects (bucket_id, name)
  values ('media', '00000000-0000-0000-0000-0000000000a2/00000000-0000-0000-0000-0000000000e6/x.jpg'),
         ('media', '00000000-0000-0000-0000-0000000000a1/00000000-0000-0000-0000-000000000002/y.jpg');

do $$
declare
  n integer;
  expected integer;
  denied boolean;
  stmt text;
  before_activity timestamptz;
begin
  if (select display_name from public.profiles where id = '00000000-0000-0000-0000-0000000000e6') <> '%' then
    raise exception 'Fixture profile not created';
  end if;

  -- ------------------------------------------------ Eve sees nothing of The Grapevine
  execute 'set local role authenticated';
  perform set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000e6', true);
  foreach stmt in array array[
    'select count(*) from public.stories where group_id = ''00000000-0000-0000-0000-0000000000a1''',
    'select count(*) from public.sources where group_id = ''00000000-0000-0000-0000-0000000000a1''',
    'select count(*) from public.messages where story_id = ''00000000-0000-0000-0000-0000000000b1''',
    'select count(*) from public.reactions',
    'select count(*) from public.message_media',
    'select count(*) from public.story_history where story_id = ''00000000-0000-0000-0000-0000000000b1''',
    'select count(*) from public.reports',
    'select count(*) from public.groups where id = ''00000000-0000-0000-0000-0000000000a1''',
    'select count(*) from public.group_members where group_id = ''00000000-0000-0000-0000-0000000000a1''',
    'select count(*) from public.profiles where id <> auth.uid()',
    'select count(*) from public.notifications where user_id <> auth.uid()',
    'select count(*) from storage.objects where name like ''00000000-0000-0000-0000-0000000000a1/%'''
  ] loop
    execute stmt into n;
    if n <> 0 then raise exception 'Outsider can read: % (% rows)', stmt, n; end if;
  end loop;

  -- ------------------------------------------------ writes members may no longer make directly
  foreach stmt in array array[
    -- forged stories, sources (even against another paper's story) and reports
    'insert into public.stories (group_id, title, summary) values (''00000000-0000-0000-0000-0000000000a2'', ''Forged'', ''not AI'')',
    'insert into public.sources (story_id, group_id, url, submitted_by) values (''00000000-0000-0000-0000-0000000000b1'', ''00000000-0000-0000-0000-0000000000a2'', ''https://x.test'', auth.uid())',
    'insert into public.reports (story_id, reporter_id, target) values (''00000000-0000-0000-0000-0000000000b9'', auth.uid(), ''title'')',
    -- choosing an invite code (e.g. an upper-case copy of another paper''s)
    'insert into public.groups (name, created_by, invite_code) values (''Copy'', auth.uid(), ''GRAPEVINEDEMO'')',
    -- backdating a message
    'insert into public.messages (story_id, author_id, body, created_at) values (''00000000-0000-0000-0000-0000000000b9'', auth.uid(), ''x'', now() - interval ''1 year'')',
    -- replying to a message in another paper
    'insert into public.messages (story_id, author_id, body, reply_to) values (''00000000-0000-0000-0000-0000000000b9'', auth.uid(), ''x'', ''00000000-0000-0000-0000-0000000000d1'')',
    -- attaching another paper''s storage object
    'insert into public.message_media (message_id, kind, url) values (''00000000-0000-0000-0000-0000000000d9'', ''image'', ''00000000-0000-0000-0000-0000000000a1/00000000-0000-0000-0000-000000000002/y.jpg'')',
    -- rewriting profile columns other than the name
    'update public.profiles set created_at = now() - interval ''9 years'' where id = auth.uid()',
    -- uploading into another paper
    'insert into storage.objects (bucket_id, name) values (''media'', ''00000000-0000-0000-0000-0000000000a1/00000000-0000-0000-0000-0000000000e6/z.jpg'')',
    -- probing another member''s notification preferences
    'select public.notifications_enabled(''00000000-0000-0000-0000-000000000001'', ''00000000-0000-0000-0000-0000000000a1'', ''mentions'')',
    -- preferences for a paper Eve is not in
    'insert into public.notification_preferences (user_id, group_id) values (auth.uid(), ''00000000-0000-0000-0000-0000000000a1'')'
  ] loop
    denied := false;
    begin execute stmt; exception when insufficient_privilege or check_violation then denied := true; end;
    if not denied then raise exception 'Was allowed: %', stmt; end if;
  end loop;

  -- ------------------------------------------------ writes the apps make still work
  insert into public.groups (id, name, created_by) values ('00000000-0000-0000-0000-0000000000a3', 'Fresh', auth.uid());
  insert into public.group_members (group_id, user_id, role) values ('00000000-0000-0000-0000-0000000000a3', auth.uid(), 'owner');
  if (select length(invite_code) from public.groups where id = '00000000-0000-0000-0000-0000000000a3') <> 12 then
    raise exception 'Default invite code not generated';
  end if;
  insert into public.messages (id, story_id, author_id, body, reply_to)
    values ('00000000-0000-0000-0000-0000000000da', '00000000-0000-0000-0000-0000000000b9', auth.uid(), 'reply', '00000000-0000-0000-0000-0000000000d9');
  insert into public.message_media (message_id, kind, url, alt_text, position) values
    ('00000000-0000-0000-0000-0000000000da', 'image', '00000000-0000-0000-0000-0000000000a2/00000000-0000-0000-0000-0000000000e6/x.jpg', 'x', 0),
    ('00000000-0000-0000-0000-0000000000da', 'gif', 'https://media.giphy.com/a.gif', 'gif', 1);
  insert into public.reactions (message_id, user_id, emoji) values ('00000000-0000-0000-0000-0000000000da', auth.uid(), '👍');
  update public.profiles set display_name = '%', monogram = '%' where id = auth.uid();
  insert into public.notification_preferences (user_id, group_id) values (auth.uid(), '00000000-0000-0000-0000-0000000000a2');
  select count(*) into n from storage.objects where name like '00000000-0000-0000-0000-0000000000a2/%';
  if n <> 1 then raise exception 'Member cannot read own paper media'; end if;

  -- ------------------------------------------------ a member's message bumps its story
  perform set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);
  select last_activity_at into before_activity from public.stories where id = '00000000-0000-0000-0000-0000000000b3';
  insert into public.messages (story_id, author_id, body) values
    ('00000000-0000-0000-0000-0000000000b3', auth.uid(), 'Ask @nika about the buses (cc 100% sure)');
  if (select last_activity_at from public.stories where id = '00000000-0000-0000-0000-0000000000b3') <= before_activity then
    raise exception 'Message did not bump story activity';
  end if;
  execute 'reset role';

  -- mentions: case-insensitive name match; a "%" display name is not a wildcard
  select count(*) into n from public.notifications
    where kind = 'mention' and user_id = '00000000-0000-0000-0000-000000000003';
  if n <> 1 then raise exception 'Mention of @nika produced % notifications', n; end if;
  if exists (select 1 from public.notifications where kind = 'mention' and user_id = '00000000-0000-0000-0000-000000000004') then
    raise exception 'A "%%" display name matched as a wildcard';
  end if;
  if exists (select 1 from public.notifications where user_id = '00000000-0000-0000-0000-0000000000e6' and group_id <> '00000000-0000-0000-0000-0000000000a2') then
    raise exception 'Outsider notified about another paper';
  end if;

  -- ------------------------------------------------ stories are announced once, when first published
  insert into public.stories (id, group_id, created_by, title, summary, flagged)
    values ('00000000-0000-0000-0000-0000000000bb', '00000000-0000-0000-0000-0000000000a1',
            '00000000-0000-0000-0000-000000000001', 'placeholder', 'Summary failed', true);
  if exists (select 1 from public.notifications where story_id = '00000000-0000-0000-0000-0000000000bb') then
    raise exception 'Placeholder story was announced';
  end if;
  update public.stories set title = 'Ready', flagged = false where id = '00000000-0000-0000-0000-0000000000bb';
  -- every member but the creator (local data may hold more members than the seed)
  select count(*) - 1 into expected from public.group_members where group_id = '00000000-0000-0000-0000-0000000000a1';
  select count(*) into n from public.notifications where story_id = '00000000-0000-0000-0000-0000000000bb' and kind = 'new_story';
  if n <> expected then raise exception 'Published story notified % members, expected %', n, expected; end if;
  update public.stories set flagged = true where id = '00000000-0000-0000-0000-0000000000bb';
  update public.stories set flagged = false where id = '00000000-0000-0000-0000-0000000000bb';
  select count(*) into n from public.notifications where story_id = '00000000-0000-0000-0000-0000000000bb' and kind = 'new_story';
  if n <> expected then raise exception 'Corrected story was announced again'; end if;
  -- a placeholder deleted before publishing never notified anyone
  insert into public.stories (id, group_id, title, summary, flagged)
    values ('00000000-0000-0000-0000-0000000000bc', '00000000-0000-0000-0000-0000000000a1', 'p', 'p', true);
  delete from public.stories where id = '00000000-0000-0000-0000-0000000000bc';
  -- a story inserted already published is announced at once (seed data, service-role inserts)
  insert into public.stories (id, group_id, title, summary)
    values ('00000000-0000-0000-0000-0000000000bd', '00000000-0000-0000-0000-0000000000a1', 'p', 'p');
  if not exists (select 1 from public.notifications where story_id = '00000000-0000-0000-0000-0000000000bd') then
    raise exception 'Published insert was not announced';
  end if;

  -- invite codes are case-insensitive and oversized input is rejected
  execute 'set local role authenticated';
  perform set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-0000000000e6', true);
  if (select id from public.group_preview_for_invite(' GRAPEVINEDEMO ')) <> '00000000-0000-0000-0000-0000000000a1' then
    raise exception 'Invite preview failed';
  end if;
  if exists (select 1 from public.group_preview_for_invite(repeat('a', 65))) then
    raise exception 'Oversized invite code accepted';
  end if;
  if public.join_group_with_invite('GrapevineDemo') <> '00000000-0000-0000-0000-0000000000a1' then
    raise exception 'Invite join failed';
  end if;
  select count(*) into n from public.stories where group_id = '00000000-0000-0000-0000-0000000000a1';
  if n = 0 then raise exception 'Joined member cannot read the paper'; end if;
  execute 'reset role';
end $$;

rollback;
