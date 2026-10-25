-- Demo data for local development: the fictional paper "The Grapevine"
-- from the design mockups. Runs automatically on `supabase db reset`.
-- All people, publishers and stories are fictional.

-- members (profiles are auto-created by the handle_new_user trigger)
-- rows must match what GoTrue itself creates, or the magic-link flow fails with
-- "Database error finding user" (GoTrue scans token columns as strings: NULL breaks it).
-- encrypted_password is a placeholder bcrypt hash matching no known password;
-- the login flow is magic link.
insert into auth.users (id, instance_id, aud, role, email, encrypted_password, email_confirmed_at, confirmation_token, recovery_token, email_change, email_change_token_new, email_change_token_current, reauthentication_token, phone_change, phone_change_token, raw_app_meta_data, raw_user_meta_data, created_at, updated_at) values
  ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'tim@example.com',  '$2a$10$2tCMgFMMmH2PA4BL8eLMbuFb6JICkI5ht2f6SvEe8FZXo.w4ny3oa', now(), '', '', '', '', '', '', '', '', '{"provider":"email","providers":["email"]}', '{"display_name":"Tim"}',  now(), now()),
  ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'luka@example.com', '$2a$10$2tCMgFMMmH2PA4BL8eLMbuFb6JICkI5ht2f6SvEe8FZXo.w4ny3oa', now(), '', '', '', '', '', '', '', '', '{"provider":"email","providers":["email"]}', '{"display_name":"Luka"}', now(), now()),
  ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'nika@example.com', '$2a$10$2tCMgFMMmH2PA4BL8eLMbuFb6JICkI5ht2f6SvEe8FZXo.w4ny3oa', now(), '', '', '', '', '', '', '', '', '{"provider":"email","providers":["email"]}', '{"display_name":"Nika"}', now(), now()),
  ('00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'jure@example.com', '$2a$10$2tCMgFMMmH2PA4BL8eLMbuFb6JICkI5ht2f6SvEe8FZXo.w4ny3oa', now(), '', '', '', '', '', '', '', '', '{"provider":"email","providers":["email"]}', '{"display_name":"Jure"}', now(), now()),
  ('00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'maja@example.com', '$2a$10$2tCMgFMMmH2PA4BL8eLMbuFb6JICkI5ht2f6SvEe8FZXo.w4ny3oa', now(), '', '', '', '', '', '', '', '', '{"provider":"email","providers":["email"]}', '{"display_name":"Maja"}', now(), now())
on conflict (id) do nothing;

insert into auth.identities (id, user_id, provider_id, provider, identity_data, last_sign_in_at, created_at, updated_at) values
  (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'email', '{"sub":"00000000-0000-0000-0000-000000000001","email":"tim@example.com","email_verified":true,"phone_verified":false}', now(), now(), now()),
  (gen_random_uuid(), '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', 'email', '{"sub":"00000000-0000-0000-0000-000000000002","email":"luka@example.com","email_verified":true,"phone_verified":false}', now(), now(), now()),
  (gen_random_uuid(), '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000003', 'email', '{"sub":"00000000-0000-0000-0000-000000000003","email":"nika@example.com","email_verified":true,"phone_verified":false}', now(), now(), now()),
  (gen_random_uuid(), '00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000004', 'email', '{"sub":"00000000-0000-0000-0000-000000000004","email":"jure@example.com","email_verified":true,"phone_verified":false}', now(), now(), now()),
  (gen_random_uuid(), '00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000005', 'email', '{"sub":"00000000-0000-0000-0000-000000000005","email":"maja@example.com","email_verified":true,"phone_verified":false}', now(), now(), now())
on conflict do nothing;

-- the group
insert into public.groups (id, name, invite_code, created_by) values
  ('00000000-0000-0000-0000-0000000000a1', 'The Grapevine', 'grapevinedemo', '00000000-0000-0000-0000-000000000001')
on conflict (id) do nothing;

-- joined_at sets each member's colour: the founder raspberry, then marigold, terra, sky, grape.
insert into public.group_members (group_id, user_id, role, last_seen_at, joined_at) values
  ('00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-000000000001', 'owner',  now() - interval '30 minutes', now() - interval '14 days'),
  ('00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-000000000002', 'member', now() - interval '2 hours',    now() - interval '13 days'),
  ('00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-000000000003', 'member', now() - interval '2 hours',    now() - interval '12 days'),
  ('00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-000000000004', 'member', now() - interval '2 hours',    now() - interval '11 days'),
  ('00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-000000000005', 'member', now() - interval '2 hours',    now() - interval '10 days')
on conflict do nothing;

-- stories
insert into public.stories (id, group_id, created_by, title, summary, category, last_activity_at) values
  ('00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-000000000002',
   'Reusable booster lands at sea on its second try',
   'Vela Orbital''s Nimbus booster landed on a barge in the North Sea on Sunday night, two months after its first attempt tipped over. Ferrite reports the next flight will carry a paying customer.',
   'Space', now() - interval '45 minutes'),
  ('00000000-0000-0000-0000-0000000000b2', '00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-000000000005',
   'Studio Kettle pushes Wayfarer to spring 2027',
   'Studio Kettle has delayed Wayfarer to spring 2027, blaming the save system. Players and the press describe the delay as frustrating but unsurprising.',
   'Games', now() - interval '3 hours'),
  ('00000000-0000-0000-0000-0000000000b3', '00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-000000000004',
   'Old town goes car-free on Sundays from March',
   'From March, the old town closes to cars on Sundays. Shop owners welcome the foot traffic; residents worry about the buses.',
   'City', now() - interval '6 hours'),
  ('00000000-0000-0000-0000-0000000000b4', '00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-000000000005',
   'Night buses to the coast stop for winter',
   'Night bus routes to the coast are suspended until April, cutting the November trip to Split for everyone who relied on them.',
   'Travel', now() - interval '2 hours'),
  ('00000000-0000-0000-0000-0000000000b5', '00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-000000000003',
   'Third-tier side knock out the champions on penalties',
   'The champions are out of the cup on penalties after a third-tier side held its nerve from the spot in front of travelling support.',
   'Sport', now() - interval '4 hours')
on conflict (id) do nothing;

-- sources (image_url points at local demo images in web/public/seed/)
insert into public.sources (id, story_id, group_id, url, domain, publisher, source_title, excerpt, image_url, author, source_published_at, submitted_by, submitter_comment, extraction_status, created_at) values
  ('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000a1',
   'https://x.com/launchwatch/status/9001', 'x.com', '@launchwatch',
   'Nimbus is down on the barge. Upright, legs out. Second attempt and they stuck it.',
   null, null, '@launchwatch', now() - interval '41 hours', '00000000-0000-0000-0000-000000000002',
   'They actually stuck it this time.', 'ok', now() - interval '41 hours'),
  ('00000000-0000-0000-0000-0000000000c2', '00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000a1',
   'https://orbitweekly.eu/vela-nimbus-lands', 'orbitweekly.eu', 'Orbit Weekly',
   'Vela lands Nimbus booster at sea, a first for a European company',
   'The barge landing closes a two-month gap since the first attempt tipped over.', 'http://localhost:3000/seed/rocket.jpg', null, now() - interval '40 hours', '00000000-0000-0000-0000-000000000003',
   'Proper write-up with the numbers.', 'ok', now() - interval '40 hours'),
  ('00000000-0000-0000-0000-0000000000c3', '00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000a1',
   'https://themeridian.com/europe-reusable-stage', 'themeridian.com', 'The Meridian',
   'Europe''s launch market gets its first reusable stage',
   null, null, null, now() - interval '7 hours', '00000000-0000-0000-0000-000000000001',
   'Paywalled, but the headline says enough.', 'paywalled', now() - interval '7 hours'),
  ('00000000-0000-0000-0000-0000000000c4', '00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000a1',
   'https://ferrite.news/vela-paying-customer', 'ferrite.news', 'Ferrite',
   'Vela''s next Nimbus flight will carry a paying customer',
   'Ferrite reports the next flight will carry a paying customer, a first for the company.', 'http://localhost:3000/seed/servers.jpg', null, now() - interval '2 hours', '00000000-0000-0000-0000-000000000003',
   'This is the actual news.', 'ok', now() - interval '2 hours'),
  ('00000000-0000-0000-0000-0000000000c5', '00000000-0000-0000-0000-0000000000b2', '00000000-0000-0000-0000-0000000000a1',
   'https://studiokettle.com/wayfarer-spring-2027', 'studiokettle.com', 'Studio Kettle',
   'Wayfarer delayed to spring 2027',
   'Studio Kettle has pushed Wayfarer to spring 2027, blaming the save system.', 'http://localhost:3000/seed/games.jpg', null, now() - interval '4 hours', '00000000-0000-0000-0000-000000000005',
   null, 'ok', now() - interval '4 hours'),
  ('00000000-0000-0000-0000-0000000000c6', '00000000-0000-0000-0000-0000000000b2', '00000000-0000-0000-0000-0000000000a1',
   'https://playlog.news/wayfarer-save-system', 'playlog.news', 'Playlog',
   'Wayfarer''s save system is the reason for the delay',
   'Developers point at the save system as the reason for the Wayfarer delay.', null, null, now() - interval '3 hours', '00000000-0000-0000-0000-000000000004',
   null, 'ok', now() - interval '3 hours'),
  ('00000000-0000-0000-0000-0000000000c7', '00000000-0000-0000-0000-0000000000b3', '00000000-0000-0000-0000-0000000000a1',
   'https://cityherald.com/old-town-car-free', 'cityherald.com', 'City Herald',
   'Old town goes car-free on Sundays from March',
   'From March the old town closes to cars on Sundays.', 'http://localhost:3000/seed/city.jpg', null, now() - interval '6 hours', '00000000-0000-0000-0000-000000000004',
   null, 'ok', now() - interval '6 hours'),
  ('00000000-0000-0000-0000-0000000000c8', '00000000-0000-0000-0000-0000000000b3', '00000000-0000-0000-0000-0000000000a1',
   'https://oldtownlive.com/car-free-shop-owners', 'oldtownlive.com', 'Old Town Live',
   'What car-free Sundays mean for shop owners',
   null, null, null, now() - interval '5 hours', '00000000-0000-0000-0000-000000000002',
   null, 'ok', now() - interval '5 hours'),
  ('00000000-0000-0000-0000-0000000000c9', '00000000-0000-0000-0000-0000000000b4', '00000000-0000-0000-0000-0000000000a1',
   'https://coastlinedaily.com/night-buses-suspended', 'coastlinedaily.com', 'Coastline Daily',
   'Night bus routes to the coast suspended until April',
   'Night bus routes to the coast are suspended until April.', 'http://localhost:3000/seed/bus.jpg', null, now() - interval '2 hours', '00000000-0000-0000-0000-000000000005',
   'There goes the November trip to Split.', 'ok', now() - interval '2 hours'),
  ('00000000-0000-0000-0000-0000000000ca', '00000000-0000-0000-0000-0000000000b5', '00000000-0000-0000-0000-0000000000a1',
   'https://x.com/tribunanorth/status/77', 'x.com', '@tribunanorth',
   'Full time: the champions are out on penalties',
   null, null, '@tribunanorth', now() - interval '4 hours', '00000000-0000-0000-0000-000000000003',
   null, 'ok', now() - interval '4 hours')
on conflict (id) do nothing;

-- messages
insert into public.messages (id, story_id, author_id, body, reply_to, created_at) values
  ('00000000-0000-0000-0000-0000000000d1', '00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-000000000002',
   'Replay from 1:42:10 if you missed it.', null, now() - interval '41 hours'),
  ('00000000-0000-0000-0000-0000000000d2', '00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-000000000004',
   'Second try is still a landing. Ask anyone how many tries the first ones took.', null, now() - interval '7 hours'),
  ('00000000-0000-0000-0000-0000000000d3', '00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-000000000005',
   'The first one tipped over in front of a camera crew. It counts as a try.', '00000000-0000-0000-0000-0000000000d2', now() - interval '7 hours'),
  ('00000000-0000-0000-0000-0000000000d4', '00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-000000000003',
   'This is the actual news.', null, now() - interval '2 hours'),
  ('00000000-0000-0000-0000-0000000000d5', '00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-000000000002',
   null, null, now() - interval '45 minutes'),
  ('00000000-0000-0000-0000-0000000000d6', '00000000-0000-0000-0000-0000000000b2', '00000000-0000-0000-0000-000000000005',
   'Fine by me if they fix the save system.', null, now() - interval '3 hours'),
  ('00000000-0000-0000-0000-0000000000d7', '00000000-0000-0000-0000-0000000000b3', '00000000-0000-0000-0000-000000000002',
   'The buses still won''t run on time on Sundays.', null, now() - interval '5 hours'),
  ('00000000-0000-0000-0000-0000000000d8', '00000000-0000-0000-0000-0000000000b5', '00000000-0000-0000-0000-000000000003',
   'Three saves. Three. In the rain.', null, now() - interval '4 hours')
on conflict (id) do nothing;

-- the GIF message (d5) prints on the pink drum
insert into public.message_media (id, message_id, kind, url, alt_text, position) values
  ('00000000-0000-0000-0000-0000000000e1', '00000000-0000-0000-0000-0000000000d5', 'gif', 'http://localhost:3000/seed/crowd.jpg', 'Crowd cheering in stage smoke', 0)
on conflict (id) do nothing;

-- reactions
insert into public.reactions (id, message_id, user_id, emoji) values
  ('00000000-0000-0000-0000-0000000000f1', '00000000-0000-0000-0000-0000000000d1', '00000000-0000-0000-0000-000000000001', '🚀'),
  ('00000000-0000-0000-0000-0000000000f2', '00000000-0000-0000-0000-0000000000d1', '00000000-0000-0000-0000-000000000003', '🚀'),
  ('00000000-0000-0000-0000-0000000000f3', '00000000-0000-0000-0000-0000000000d1', '00000000-0000-0000-0000-000000000004', '👏'),
  ('00000000-0000-0000-0000-0000000000f4', '00000000-0000-0000-0000-0000000000d2', '00000000-0000-0000-0000-000000000005', '😂'),
  ('00000000-0000-0000-0000-0000000000f5', '00000000-0000-0000-0000-0000000000d2', '00000000-0000-0000-0000-000000000001', '😂'),
  ('00000000-0000-0000-0000-0000000000f6', '00000000-0000-0000-0000-0000000000d5', '00000000-0000-0000-0000-000000000005', '❤️')
on conflict do nothing;

-- every story keeps its first generated version in history
insert into public.story_history (id, story_id, title, summary, category, created_at) values
  ('00000000-0000-0000-0000-000000000111', '00000000-0000-0000-0000-0000000000b1',
   'Reusable booster lands at sea on its second try',
   'Vela Orbital''s Nimbus booster landed on a barge in the North Sea on Sunday night, two months after its first attempt tipped over.',
   'Space', now() - interval '40 hours'),
  ('00000000-0000-0000-0000-000000000112', '00000000-0000-0000-0000-0000000000b2',
   'Studio Kettle pushes Wayfarer to spring 2027',
   'Studio Kettle has delayed Wayfarer to spring 2027, blaming the save system.',
   'Games', now() - interval '4 hours'),
  ('00000000-0000-0000-0000-000000000113', '00000000-0000-0000-0000-0000000000b3',
   'Old town goes car-free on Sundays from March',
   'From March, the old town closes to cars on Sundays.',
   'City', now() - interval '6 hours'),
  ('00000000-0000-0000-0000-000000000114', '00000000-0000-0000-0000-0000000000b4',
   'Night buses to the coast stop for winter',
   'Night bus routes to the coast are suspended until April.',
   'Travel', now() - interval '2 hours'),
  ('00000000-0000-0000-0000-000000000115', '00000000-0000-0000-0000-0000000000b5',
   'Third-tier side knock out the champions on penalties',
   'The champions are out of the cup on penalties after a third-tier side held its nerve from the spot.',
   'Sport', now() - interval '4 hours')
on conflict (id) do nothing;

-- admin: tim@example.com can open /admin on the web
insert into public.admins (user_id) values ('00000000-0000-0000-0000-000000000001')
on conflict do nothing;

-- a sample correction report so the admin reports page has a row
insert into public.reports (id, story_id, reporter_id, target, note, status, created_at, resolved_at) values
  ('00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-0000000000b2', '00000000-0000-0000-0000-000000000003',
   'summary', 'The delay is to spring, not summer.', 'resolved', now() - interval '3 hours', now() - interval '3 hours'),
  ('00000000-0000-0000-0000-000000000202', '00000000-0000-0000-0000-0000000000b4', '00000000-0000-0000-0000-000000000004',
   'category', 'This is more City than Travel.', 'open', now() - interval '40 minutes', null)
on conflict (id) do nothing;

-- sample AI usage: one submission per story over the last two weeks, priced like the edge functions price them
insert into public.ai_usage (created_at, request_id, function_name, operation, provider, model,
  input_tokens, output_tokens, cost_usd, latency_ms, user_id, group_id, story_id)
select r.at, r.request_id, 'submit-source', c.operation, c.provider, c.model, c.input_tokens, c.output_tokens,
  (c.input_tokens * c.input_rate + c.output_tokens * c.output_rate) / 1000000.0, c.latency_ms,
  r.user_id, '00000000-0000-0000-0000-0000000000a1', r.story_id
from (values
  ('00000000-0000-0000-0000-000000000301'::uuid, now() - interval '13 days', '00000000-0000-0000-0000-000000000002'::uuid, '00000000-0000-0000-0000-0000000000b1'::uuid),
  ('00000000-0000-0000-0000-000000000302'::uuid, now() - interval '9 days',  '00000000-0000-0000-0000-000000000003'::uuid, '00000000-0000-0000-0000-0000000000b2'::uuid),
  ('00000000-0000-0000-0000-000000000303'::uuid, now() - interval '5 days',  '00000000-0000-0000-0000-000000000004'::uuid, '00000000-0000-0000-0000-0000000000b3'::uuid),
  ('00000000-0000-0000-0000-000000000304'::uuid, now() - interval '2 days',  '00000000-0000-0000-0000-000000000005'::uuid, '00000000-0000-0000-0000-0000000000b4'::uuid),
  ('00000000-0000-0000-0000-000000000305'::uuid, now() - interval '4 hours', '00000000-0000-0000-0000-000000000001'::uuid, '00000000-0000-0000-0000-0000000000b5'::uuid)
) as r (request_id, at, user_id, story_id)
cross join (values
  ('embeddings',       'mistral', 'mistral-embed',        180,  0, 0.10, 0.00, 210),
  ('clustering',       'openai',  'gpt-6-luna',           240,  0, 0.10, 0.00, 380),
  ('chat/completions', 'mistral', 'mistral-large-latest', 620, 18, 0.50, 1.50, 1400),
  ('chat/completions', 'mistral', 'mistral-large-latest', 640, 190, 0.50, 1.50, 3900),
  ('chat/completions', 'mistral', 'mistral-small-latest', 610, 3, 0.15, 0.60, 520),
  ('embeddings',       'mistral', 'mistral-embed',        230,  0, 0.10, 0.00, 190)
) as c (operation, provider, model, input_tokens, output_tokens, input_rate, output_rate, latency_ms)
where not exists (select 1 from public.ai_usage);
