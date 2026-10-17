'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { App } from '@capacitor/app';
import { checkEdition } from './actions';
import { isNativeApp } from '@/lib/capacitor';
import styles from './paper.module.css';

export default function LaterEdition({ groupId, order }: { groupId: string; order: string[] }) {
  const router = useRouter();
  const [moved, setMoved] = useState(0);
  useEffect(() => {
    let active = true;
    const tick = async () => {
      try {
        const next = await checkEdition(groupId);
        const changed = new Set([...order, ...next].filter(id => order.indexOf(id) !== next.indexOf(id)));
        if (active) setMoved(changed.size);
      } catch { /* Keep the printed edition during temporary connection failures. */ }
    };
    const timer = setInterval(tick, 60_000);
    let resume: { remove: () => Promise<void> } | undefined;
    if (isNativeApp) {
      // The paper refreshes when the app returns to the foreground.
      void App.addListener('appStateChange', ({ isActive }) => { if (isActive) void tick(); }).then(handle => { resume = handle; });
    }
    return () => { active = false; clearInterval(timer); void resume?.remove(); };
  }, [groupId, order]);
  return moved > 0 ? <button className={styles.later} onClick={() => router.refresh()}>↑ Later edition · {moved} {moved === 1 ? 'story' : 'stories'} moved</button> : null;
}
