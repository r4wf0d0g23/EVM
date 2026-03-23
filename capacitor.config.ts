import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.evefrontier.vault',
  appName: 'EVE Vault',
  webDir: '../evevault/apps/web/dist',
  server: {
    androidScheme: 'https',
    // Allow deep links from EVE Frontier OAuth
    allowNavigation: [
      'accounts.google.com',
      'fullnode.testnet.sui.io',
      'lbmfdkobfnkfobfahpekbaaombpnafah.chromiumapp.org',
    ],
  },
  android: {
    buildOptions: {
      minSdkVersion: 24,
      targetSdkVersion: 34,
    },
  },
  plugins: {
    Preferences: {
      group: 'EVEVaultStorage',
    },
  },
};

export default config;
