import { App } from '@capacitor/app';
import { Browser } from '@capacitor/browser';
import { Clipboard } from '@capacitor/clipboard';
import { Share } from '@capacitor/share';
import { StatusBar, Style } from '@capacitor/status-bar';

const SITE = 'https://thegrapevine.si';

let native = false;
try {
  const capacitor = (window as { Capacitor?: { isNativePlatform?: () => boolean } }).Capacitor;
  native = typeof window !== 'undefined' && typeof capacitor?.isNativePlatform === 'function' && capacitor.isNativePlatform();
} catch {
  native = false;
}

export const isNativeApp = native;

/** http(s) links outside the app open in the system browser, not the WebView. */
export function openExternal(url: string) {
  if (native) return Browser.open({ url });
  window.open(url, '_blank', 'noopener,noreferrer');
}

export async function shareOrCopy(text: string) {
  if (native) {
    try {
      await Share.share({ text });
      return;
    } catch {
      // Share sheet cancelled — fall through to the clipboard.
    }
  }
  if (native) await Clipboard.write({ string: text });
  else if (navigator.clipboard) await navigator.clipboard.writeText(text);
}

/**
 * Sign-in magic links redirect to grapevine://auth/callback. On native Android the
 * app intercepts that scheme; the site's /auth/callback route then finishes the
 * session exchange inside the WebView, so we hand the whole URL over to the site.
 */
export function handleAuthDeepLink(url: string): string | null {
  if (!native) return null;
  try {
    const parsed = new URL(url);
    if (parsed.protocol !== 'grapevine:') return null;
    return `${SITE}${parsed.pathname}${parsed.search}${parsed.hash}`;
  } catch {
    return null;
  }
}

/** Match the native status bar to the paper background. */
export async function syncStatusBar() {
  if (!native) return;
  try {
    await StatusBar.setStyle({ style: Style.Light });
    await StatusBar.setBackgroundColor({ color: '#EFEEE9' });
    await StatusBar.setOverlaysWebView({ overlay: false });
  } catch {
    // Older WebView or plugin failure — cosmetic only.
  }
}

/** Keep the Capacitor back button in step with the site's history. */
export function registerBackHandler(onBack: () => boolean) {
  if (!native) return () => {};
  let removed = false;
  App.addListener('backButton', ({ canGoBack }) => {
    if (canGoBack) window.history.back();
    else if (!onBack()) App.exitApp();
  }).then(listener => { if (removed) void listener.remove(); });
  return () => { removed = true; };
}
