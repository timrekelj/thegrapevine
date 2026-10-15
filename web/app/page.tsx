import Link from 'next/link';
import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { getPaperStories } from '@/lib/queries';
import type { PaperMember, RankedStory } from '@/lib/types';
import LaterEdition from '@/components/paper/later-edition';
import ShareLink from '@/components/paper/share-link';
import styles from '@/components/paper/paper.module.css';
import { connection } from 'next/server';

function Plate({ story, hero = false }: { story: RankedStory; hero?: boolean }) {
  const size = hero ? styles.hero : styles.thumb;
  return story.imageUrl ? <div className={`riso ${size}`}>
    <div className="plate"><div className="ht">
      {/* Publisher preview domains are arbitrary; the halftone plate handles sizing. */}
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src={story.imageUrl} alt="" />
    </div></div>
  </div> : <div className={`noimg ${size}`}><span>{story.publishers[0] || 'The paper'}</span></div>;
}

function StoryMeta({ story, lead = false }: { story: RankedStory; lead?: boolean }) {
  return <div className={`meta tnum ${styles.meta}`}>
    <span><b>{story.sourceCount}</b> {story.sourceCount === 1 ? 'source' : 'sources'}{lead && story.publishers.length > 0 ? `: ${story.publishers.join(', ')}` : ''}</span>
    <span><b>{story.messageCount}</b> {story.messageCount === 1 ? 'message' : 'messages'}</span>
    {story.newSources > 0 && <span className="new">{story.newSources} new {story.newSources === 1 ? 'source' : 'sources'}</span>}
    {story.newMessages > 0 && <span className="new">{story.newMessages} new {story.newMessages === 1 ? 'message' : 'messages'}</span>}
    {story.submitters.length > 0 && <span className={styles.submitters} aria-label={`Shared by ${story.submitters.map(m => m.display_name).join(', ')}`}>
      {story.submitters.map(m => <span className="av" key={m.id} title={m.display_name}>{m.monogram}</span>)}
    </span>}
  </div>;
}

export default async function Home() {
  await connection() // fully dynamic: session + database
  const client = await createClient();
  const { data: { user } } = await client.auth.getUser();
  if (!user) redirect('/login');
  const { data: membership, error: membershipError } = await client.from('group_members')
    .select('group_id,last_seen_at').eq('user_id', user.id).order('joined_at').limit(1).maybeSingle();
  if (membershipError) throw membershipError;
  if (!membership) redirect('/onboarding');
  const groupId = membership.group_id;
  const loadedAt = new Date();
  const [{ data: group, error: groupError }, { data: memberRows, error: membersError }] = await Promise.all([
    client.from('groups').select('name').eq('id', groupId).single(),
    client.from('group_members').select('profiles(id,display_name,monogram)').eq('group_id', groupId).order('joined_at'),
  ]);
  if (groupError) throw groupError;
  if (membersError) throw membersError;
  const members = memberRows.flatMap(row => row.profiles) as unknown as PaperMember[];
  const stories = await getPaperStories(client, groupId, membership.last_seen_at, members);
  // Read the previous timestamp before advancing it, and do not mark a failed load as seen.
  const { data: seenRows, error: seenError } = await client.from('group_members')
    .update({ last_seen_at: loadedAt.toISOString() }).eq('group_id', groupId).eq('user_id', user.id).select('user_id');
  if (seenError) throw seenError;
  if (!seenRows?.length) throw new Error('Could not record this edition as seen. Check the member update policy.');
  const [lead, ...rest] = stories;
  const categories = [...new Set(stories.map(s => s.category).filter(Boolean))];
  const conversation = stories.flatMap(story => story.messages.map(message => ({ ...message, story })))
    .sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at) || a.id.localeCompare(b.id)).slice(0, 4);
  return <main className={styles.paper}>
    <header className={styles.top}>
      <time className={styles.date} dateTime={loadedAt.toISOString()}>{loadedAt.toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}</time>
      <h1 className="masthead">{group.name}</h1>
      <div className={styles.right}>
        <ShareLink groupId={groupId} groupName={group.name} />
        <div className={styles.members} aria-label="At the table">{members.map(member => <span className="av" key={member.id} title={member.display_name}>{member.monogram}</span>)}</div>
      </div>
    </header>
    <nav className={styles.sections} aria-label="Sections"><b>Front page</b>{categories.map(category => <span key={category}>{category}</span>)}<input className={styles.search} placeholder="Search the paper" aria-label="Search the paper (coming soon)" readOnly /></nav>
    <LaterEdition key={stories.map(story => story.id).join(',')} groupId={groupId} order={stories.map(story => story.id)} />
    {lead ? <div className={styles.grid}>
      <article className={styles.lead}>
        <Link href={`/story/${lead.id}`}>
          <Plate story={lead} hero />
          <div className={styles.category}><span className="label">{lead.category || 'News'}</span>{lead.newSources > 0 && <span className="stamp rest">+{lead.newSources} {lead.newSources === 1 ? 'source' : 'sources'}</span>}</div>
          <h2 className="display">{lead.title || 'A link from the table'}</h2>
          <p className="summary">{lead.summary || 'The summary is still being prepared. Open the story to read the shared link.'}</p>
          <StoryMeta story={lead} lead />
        </Link>
      </article>
      <aside className={styles.side} aria-label="More stories">{rest.map(story => <article key={story.id}>
        <Link className={styles.story} href={`/story/${story.id}`}>
          <div><div className="label">{story.category || 'News'}</div><h3 className="headline">{story.title || 'A link from the table'}</h3><StoryMeta story={story} /></div>
          <Plate story={story} />
        </Link>
      </article>)}</aside>
    </div> : <section className={styles.empty}><h2 className="headline">Share the first link to start the paper.</h2><ShareLink groupId={groupId} groupName={group.name} /></section>}
    {conversation.length > 0 && <section className={styles.table}>
      <h2>At the table <small>Latest from the conversation</small></h2>
      <div className={styles.lines}>{conversation.map(message => <Link className={styles.line} href={`/story/${message.story_id}`} key={message.id}>
        <q>{message.body || 'Shared something with the table.'}</q>
        <div className="meta"><b>{members.find(m => m.id === message.author_id)?.display_name || 'A member'}</b> on {message.story.title} · <time dateTime={message.created_at}>{new Date(message.created_at).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })}</time></div>
      </Link>)}</div>
    </section>}
  </main>;
}
