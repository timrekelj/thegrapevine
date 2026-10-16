import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'si.timrekelj.thegrapevine',
  appName: 'The Grapevine',
  // The app is a native shell around the deployed site. `npx cap sync` copies the
  // Next.js build into android/app/src/main/assets/public only for local runs;
  // release builds boot from the hosted origin below.
  webDir: 'out',
  server: {
    url: 'https://thegrapevine.si',
    cleartext: false,
    androidScheme: 'https',
  },
  android: {
    allowMixedContent: false,
    captureInput: true,
    webContentsDebuggingEnabled: false,
  },
  plugins: {
    SplashScreen: {
      launchShowDuration: 1200,
      launchAutoHide: true,
      backgroundColor: '#EFEEE9',
      androidSplashResourceName: 'splash',
      showSpinner: false,
    },
    StatusBar: {
      style: 'LIGHT',
      backgroundColor: '#EFEEE9',
      overlaysWebView: false,
    },
  },
};

export default config;
