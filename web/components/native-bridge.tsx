'use client';

import { useEffect } from 'react';
import { usePathname } from 'next/navigation';
import { handleAuthDeepLink, isNativeApp, registerBackHandler, syncStatusBar } from '@/lib/capacitor';

/**
 * Wires the Capacitor shell to the site:
 * - a grapevine://auth/callback deep link (magic-link sign-in) is rewritten to the
 *   hosted /auth/callback route and navigated to in-app;
 * - the status bar matches the paper background;
 * - the hardware back button follows the site history instead of closing the app.
 */
export default function NativeBridge() {
  const pathname = usePathname();

  useEffect(() => {
    if (!isNativeApp) return;
    syncStatusBar();

    const current = `${window.location.origin}${window.location.pathname}${window.location.search}${window.location.hash}`;
    const target = handleAuthDeepLink(current);
    if (target && target !== current) window.location.replace(target);
  }, []);

  useEffect(() => {
    if (!isNativeApp) return;
    return registerBackHandler(() => {
      window.dispatchEvent(new PopStateEvent('popstate'));
      return true;
    });
  }, [pathname]);

  return null;
}
