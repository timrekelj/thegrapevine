'use client';

import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import styles from './paper.module.css';

// The Supabase client loads on first use: `/` is also the landing page, which never needs it.
const supabase = () => import('@/lib/supabase/client').then(({ createClient }) => createClient());

type Submission = { status: 'new_story' | 'joined' | 'duplicate'; source_id?: string; story_id?: string; story_title?: string; title?: string };

export default function ShareLink({ groupId, groupName }: { groupId: string; groupName: string }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const headingId = useId();
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const router = useRouter();
  const [url, setUrl] = useState('');
  const [comment, setComment] = useState('');
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState('');
  const [error, setError] = useState('');
  const [toast, setToast] = useState<{ text: string; sourceId?: string } | null>(null);
  useEffect(() => {
    if (!toast) return;
    const dismiss = setTimeout(() => setToast(null), 10_000);
    return () => clearTimeout(dismiss);
  }, [toast]);
  useEffect(() => () => { if (timer.current) clearTimeout(timer.current); }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setError('');
    let parsed: URL;
    try {
      parsed = new URL(url.trim());
      if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error();
    } catch {
      setError('Enter a complete http or https link.');
      return;
    }
    setBusy(true);
    setStatus(`Reading ${parsed.hostname}…`);
    timer.current = setTimeout(() => setStatus('Looking for the same event…'), 1400);
    try {
      const client = await supabase();
      const { data, error: invokeError } = await client.functions.invoke<Submission>('submit-source', {
        body: { group_id: groupId, url: parsed.href, comment: comment.trim() || undefined },
      });
      if (invokeError) {
        if (invokeError.context instanceof Response) {
          const failure = await invokeError.context.json().catch(() => null);
          if (failure?.status === 'ai_failed') {
            router.refresh();
            setToast({ text: 'Source saved. AI organization failed; open the story to report it and retry.' });
            dialog.current?.close();
            setUrl('');
            setComment('');
            return;
          }
          if (typeof failure?.error === 'string') throw new Error(failure.error);
        }
        throw invokeError;
      }
      if (!data || !['new_story', 'joined', 'duplicate'].includes(data.status)) {
        throw new Error('The filing result could not be confirmed. Your link and comment are still here.');
      }
      if (data.status === 'duplicate') {
        setToast({ text: 'Your contribution was not added because it was a duplicate.' });
      } else {
        let title = data.story_title || data.title;
        if (!title && data.story_id) {
          const result = await client.from('stories').select('title').eq('id', data.story_id).maybeSingle();
          title = result.data?.title;
        }
        setToast(data.status === 'joined'
          ? { text: `Filed under ${title || 'the story'}`, sourceId: data.source_id }
          : { text: `New story: ${title || 'Your shared link'}` });
        router.refresh();
      }
      dialog.current?.close();
      setUrl('');
      setComment('');
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Could not post. Your link and comment are still here.');
    } finally {
      if (timer.current) clearTimeout(timer.current);
      setBusy(false);
      setStatus('');
    }
  }

  async function undo() {
    if (!toast?.sourceId || busy) return;
    setBusy(true);
    try {
      const { data, error } = await (await supabase()).from('sources').delete().eq('id', toast.sourceId).select('id');
      if (error || !data?.length) throw new Error('Could not undo this contribution.');
      setToast({ text: 'Contribution removed.' });
      router.refresh();
    } catch (failure) {
      setToast({ text: failure instanceof Error ? failure.message : 'Could not undo this contribution.', sourceId: toast.sourceId });
    } finally { setBusy(false); }
  }

  return <>
    <button className="btn" onClick={() => { setError(''); dialog.current?.showModal(); }}><svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true"><path d="M6 1v10M1 6h10" stroke="currentColor" strokeWidth="1.6" /></svg>Share a link</button>
    <dialog ref={dialog} className={styles.dialog} onCancel={event => { if (busy) event.preventDefault(); }} aria-labelledby={headingId}>
      <form onSubmit={submit}>
        <header className={styles.dialogHead}><span className="label">Filing for {groupName}</span><h2 id={headingId} className="headline">Share a link</h2></header>
        <label>Link<input type="url" required autoFocus placeholder="https://…" value={url} disabled={busy} onChange={e => setUrl(e.target.value)} /></label>
        <label><span>Your comment <span className={styles.optional}>(optional)</span></span><textarea value={comment} disabled={busy} onChange={e => setComment(e.target.value)} /></label>
        <p className="meta">Everyone in {groupName} sees this with your name.</p>
        {status && <p className="meta" role="status">{status}</p>}
        {error && <p className={styles.error} role="alert">{error}</p>}
        <div className={styles.dialogActions}>
          <button type="button" className={styles.cancel} disabled={busy} onClick={() => dialog.current?.close()}>Cancel</button>
          <button className="btn" type="submit" disabled={busy}>{busy ? 'Filing…' : 'Post'}</button>
        </div>
      </form>
    </dialog>
    {toast && <div className={styles.toast} role="status">{toast.text}{toast.sourceId && <> · <button disabled={busy} onClick={undo}>Undo</button></>}</div>}
  </>;
}
