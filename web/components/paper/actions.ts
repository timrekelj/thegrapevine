'use server';

import { createClient } from '@/lib/supabase/server';
import { getPaperStories } from '@/lib/queries';

export async function checkEdition(groupId: string) {
  const client = await createClient();
  const { data: { user }, error } = await client.auth.getUser();
  if (error || !user) throw new Error('Please sign in again.');
  const { data: member, error: membershipError } = await client.from('group_members')
    .select('group_id').eq('group_id', groupId).eq('user_id', user.id).maybeSingle();
  if (membershipError || !member) throw new Error('Paper unavailable.');
  return (await getPaperStories(client, groupId, null)).map(story => story.id);
}
