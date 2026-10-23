'use client';

import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import { BusyLabel } from '@/components/print/InkDots';
import styles from './paper.module.css';

// The Supabase client loads on first use: `/` is also the landing page, which never needs it.
const supabase = () => import('@/lib/supabase/client').then(({ createClient }) => createClient());

type Submission = { status: 'accepted' | 'duplicate' };
type Receipt = { kind: 'source_filed' | 'source_failed'; story_id: string | null };

// Filing continues on the server after it accepts the link; the receipt arrives as a notification.
const RECEIPT_WAIT_MS = 3 * 60_000;

export default function ShareLink({ groupId, groupName }: { groupId: string; groupName: string }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const headingId = useId();
  const listening = useRef(new Set<() => void>());
  const router = useRouter();
  const [url, setUrl] = useState('');
  const [comment, setComment] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [toast, setToast] = useState<string | null>(null);
  useEffect(() => {
    if (!toast) return;
    const dismiss = setTimeout(() => setToast(null), 10_000);
    return () => clearTimeout(dismiss);
  }, [toast]);
  useEffect(() => { const stops = listening.current; return () => stops.forEach(stop => stop()); }, []);

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
    const client = await supabase();
    // Listen before posting, so a quick receipt is not missed.
    const listen = await receipts(client);
    try {
      const { data, error: invokeError } = await client.functions.invoke<Submission>('submit-source', {
        body: { group_id: groupId, url: parsed.href, comment: comment.trim() || undefined },
      });
      if (invokeError) {
        if (invokeError.context instanceof Response) {
          const failure = await invokeError.context.json().catch(() => null);
          if (typeof failure?.error === 'string') throw new Error(failure.error);
        }
        throw invokeError;
      }
      if (!data || !['accepted', 'duplicate'].includes(data.status)) {
        throw new Error('The filing result could not be confirmed. Your link and comment are still here.');
      }
      if (data.status === 'duplicate') {
        listen.stop();
        setToast('Your contribution was not added because it was a duplicate.');
      } else {
        listening.current.add(listen.stop);
        setToast(`Filing ${parsed.hostname}… It will be on the paper in a moment.`);
        listen.receipt.then(receipt => {
          listening.current.delete(listen.stop);
          router.refresh();
          if (!receipt) return;
          setToast(receipt.kind === 'source_filed' ? 'Your link is on the paper.'
            : receipt.story_id ? 'Your link was saved, but it could not be summarized. Open the story to report it and retry.'
            : 'Your link could not be shared. Please try again.');
        });
      }
      dialog.current?.close();
      setUrl('');
      setComment('');
    } catch (failure) {
      listen.stop();
      setError(failure instanceof Error ? failure.message : 'Could not post. Your link and comment are still here.');
    } finally {
      setBusy(false);
    }
  }

  /** The next filing receipt for this member, or null if none arrives in time or listening stops. */
  async function receipts(client: Awaited<ReturnType<typeof supabase>>) {
    let settle!: (receipt: Receipt | null) => void;
    const receipt = new Promise<Receipt | null>(resolve => { settle = resolve; });
    const channel = client.channel(`receipts:${crypto.randomUUID()}`)
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'notifications', filter: 'kind=in.(source_filed,source_failed)' },
        payload => stop(payload.new as Receipt));
    const timeout = setTimeout(() => stop(null), RECEIPT_WAIT_MS);
    function stop(value: Receipt | null = null) {
      clearTimeout(timeout);
      settle(value);
      void client.removeChannel(channel);
    }
    // Any outcome will do: if listening fails, the paper still refreshes when the wait runs out.
    await new Promise<void>(resolve => channel.subscribe(() => resolve()));
    return { receipt, stop: () => stop() };
  }

  return <>
    <button className="btn" onClick={() => { setError(''); dialog.current?.showModal(); }}><svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true"><path d="M6 1v10M1 6h10" stroke="currentColor" strokeWidth="1.6" /></svg>Share a link</button>
    <dialog ref={dialog} className={styles.dialog} onCancel={event => { if (busy) event.preventDefault(); }} aria-labelledby={headingId}>
      <form onSubmit={submit}>
        <header className={styles.dialogHead}><span className="label">Filing for {groupName}</span><h2 id={headingId} className="headline">Share a link</h2></header>
        <label>Link<input type="url" required autoFocus placeholder="https://…" value={url} disabled={busy} onChange={e => setUrl(e.target.value)} /></label>
        <label><span>Your comment <span className={styles.optional}>(optional)</span></span><textarea value={comment} disabled={busy} onChange={e => setComment(e.target.value)} /></label>
        <p className="meta">Everyone in {groupName} sees this with your name.</p>
        {error && <p className={styles.error} role="alert">{error}</p>}
        <div className={styles.dialogActions}>
          <button type="button" className={styles.cancel} disabled={busy} onClick={() => dialog.current?.close()}>Cancel</button>
          <button className="btn" type="submit" disabled={busy} aria-busy={busy}><BusyLabel busy={busy} label="Filing…">Post</BusyLabel></button>
        </div>
      </form>
    </dialog>
    {toast && <div className={styles.toast} role="status">{toast}</div>}
  </>;
}
