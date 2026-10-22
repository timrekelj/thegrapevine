'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { checkEdition } from './actions';
import styles from './paper.module.css';

export default function LaterEdition({ groupId, order }: { groupId: string; order: string[] }) {
  const router = useRouter();
  const [moved, setMoved] = useState(0);
  useEffect(() => {
    let active = true;
    const timer = setInterval(async () => {
      try {
        const next = await checkEdition(groupId);
        const changed = new Set([...order, ...next].filter(id => order.indexOf(id) !== next.indexOf(id)));
        if (active) setMoved(changed.size);
      } catch { /* Keep the printed edition during temporary connection failures. */ }
    }, 60_000);
    return () => { active = false; clearInterval(timer); };
  }, [groupId, order]);
  return moved > 0 ? <button className={styles.later} onClick={() => router.refresh()}>↑ Later edition · {moved} {moved === 1 ? 'story' : 'stories'} moved · Reload</button> : null;
}
