import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { currentUserId } from '@/lib/auth';
import { loadPaperStories, printEdition } from '@/lib/queries';
import type { PaperMember } from '@/lib/types';
import FrontPage from '@/components/paper/FrontPage';
import Landing from '@/components/landing/Landing';
import { connection } from 'next/server';

export default async function Home() {
  await connection() // fully dynamic: session + database
  const client = await createClient();
  const userId = await currentUserId(client);
  // Signed-out visitors get the landing page; the paper lives at the same address once signed in.
  if (!userId) return <Landing />;
  const { data: membership, error: membershipError } = await client.from('group_members')
    .select('group_id,last_seen_at').eq('user_id', userId).order('joined_at').limit(1).maybeSingle();
  if (membershipError) throw membershipError;
  if (!membership) redirect('/onboarding');
  const groupId = membership.group_id;
  const loadedAt = new Date();
  const [{ data: group, error: groupError }, { data: memberRows, error: membersError }, edition] = await Promise.all([
    client.from('groups').select('name,created_at').eq('id', groupId).single(),
    client.from('group_members').select('profiles(id,display_name,monogram)').eq('group_id', groupId).order('joined_at'),
    loadPaperStories(client, groupId),
  ]);
  if (groupError) throw groupError;
  if (membersError) throw membersError;
  const members = memberRows.flatMap(row => row.profiles) as unknown as PaperMember[];
  const stories = printEdition(edition.stories, membership.last_seen_at, members);
  // Read the previous timestamp before advancing it, and do not mark a failed load as seen.
  const { data: seenRows, error: seenError } = await client.from('group_members')
    .update({ last_seen_at: loadedAt.toISOString() }).eq('group_id', groupId).eq('user_id', userId).select('user_id');
  if (seenError) throw seenError;
  if (!seenRows?.length) throw new Error('Could not record this edition as seen. Check the member update policy.');
  return <FrontPage group={{ id: groupId, ...group }} members={members} stories={stories} earlier={edition.earlier} loadedAt={loadedAt} />;
}
