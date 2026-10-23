'use client';

import { useEffect, useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import { plural } from '@/lib/format';
import { BusyLabel } from '@/components/print/InkDots';
import { checkEdition } from './actions';
import styles from './paper.module.css';

export default function LaterEdition({ groupId, order }: { groupId: string; order: string[] }) {
  const router = useRouter();
  const [moved, setMoved] = useState(0);
  const [reprinting, reprint] = useTransition();
  useEffect(() => {
    let active = true;
    const timer = setInterval(async () => {
      // A background tab has no one to tell; it checks again when it is next looked at.
      if (document.visibilityState !== 'visible') return;
      try {
        const next = await checkEdition(groupId);
        const changed = new Set([...order, ...next].filter(id => order.indexOf(id) !== next.indexOf(id)));
        if (active) setMoved(changed.size);
      } catch { /* Keep the printed edition during temporary connection failures. */ }
    }, 60_000);
    return () => { active = false; clearInterval(timer); };
  }, [groupId, order]);
  return moved > 0 ? <button className={styles.later} disabled={reprinting} aria-busy={reprinting} onClick={() => reprint(() => router.refresh())}>
    <BusyLabel busy={reprinting} label="Printing the later edition…">↑ Later edition · {plural(moved, 'story', 'stories')} moved · Reload</BusyLabel>
  </button> : null;
}
