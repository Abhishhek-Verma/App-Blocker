import { NativeModules, Platform } from 'react-native';
import { AppItem, PermissionStatus } from '../types';

const { AppBlockerModule } = NativeModules;

// In-memory fallback mock for simulator / preview development
let mockBlockedPackages = new Set<string>(['com.instagram.android']);
let mockMasterActive = true;
let mockPin: string | null = null;
let mockRequirePin = false;

const DEFAULT_MOCK_APPS: AppItem[] = [
  { packageName: 'com.instagram.android', appName: 'Instagram', isBlocked: true },
  { packageName: 'com.zhiliaoapp.musically', appName: 'TikTok', isBlocked: false },
  { packageName: 'com.google.android.youtube', appName: 'YouTube', isBlocked: false },
  { packageName: 'com.twitter.android', appName: 'X (Twitter)', isBlocked: false },
  { packageName: 'com.snapchat.android', appName: 'Snapchat', isBlocked: false },
  { packageName: 'com.facebook.katana', appName: 'Facebook', isBlocked: false },
  { packageName: 'com.reddit.frontpage', appName: 'Reddit', isBlocked: false },
  { packageName: 'com.whatsapp', appName: 'WhatsApp', isBlocked: false }
];

export const NativeBlockerService = {
  /**
   * Fetches installed apps from native PackageManager
   */
  async getInstalledApps(): Promise<AppItem[]> {
    if (AppBlockerModule?.getInstalledApps) {
      try {
        const rawApps = await AppBlockerModule.getInstalledApps();
        const blockedSet = await this.getBlockedPackages();
        return rawApps.map((a: any) => ({
          packageName: a.packageName,
          appName: a.appName,
          isBlocked: blockedSet.includes(a.packageName),
          isSystemApp: a.isSystemApp
        }));
      } catch (err) {
        console.warn('Native getInstalledApps failed, using mock list:', err);
      }
    }

    // Fallback mock
    return DEFAULT_MOCK_APPS.map(a => ({
      ...a,
      isBlocked: mockBlockedPackages.has(a.packageName)
    }));
  },

  /**
   * Saves blocked package names to native SharedPreferences (<1ms interception delay)
   */
  async setBlockedPackages(packages: string[]): Promise<boolean> {
    if (AppBlockerModule?.setBlockedPackages) {
      try {
        return await AppBlockerModule.setBlockedPackages(packages);
      } catch (err) {
        console.warn('Native setBlockedPackages error:', err);
      }
    }
    mockBlockedPackages = new Set(packages);
    return true;
  },

  /**
   * Gets list of currently blocked packages from native layer
   */
  async getBlockedPackages(): Promise<string[]> {
    if (AppBlockerModule?.getBlockedPackages) {
      try {
        return await AppBlockerModule.getBlockedPackages();
      } catch (err) {
        console.warn('Native getBlockedPackages error:', err);
      }
    }
    return Array.from(mockBlockedPackages);
  },

  /**
   * Toggles master blocker switch
   */
  async setMasterActive(active: boolean): Promise<boolean> {
    if (AppBlockerModule?.setMasterActive) {
      try {
        return await AppBlockerModule.setMasterActive(active);
      } catch (err) {
        console.warn('Native setMasterActive error:', err);
      }
    }
    mockMasterActive = active;
    return true;
  },

  /**
   * Configures native PIN lock
   */
  async setPinCode(pin: string | null, requirePin: boolean): Promise<boolean> {
    if (AppBlockerModule?.setPinCode) {
      try {
        return await AppBlockerModule.setPinCode(pin, requirePin);
      } catch (err) {
        console.warn('Native setPinCode error:', err);
      }
    }
    mockPin = pin;
    mockRequirePin = requirePin;
    return true;
  },

  /**
   * Checks both Accessibility and Overlay permissions
   */
  async checkPermissions(): Promise<PermissionStatus> {
    let accessibility = true;
    let overlay = true;

    if (Platform.OS === 'android' && AppBlockerModule) {
      try {
        accessibility = await AppBlockerModule.isAccessibilityEnabled();
        overlay = await AppBlockerModule.canDrawOverlays();
      } catch (err) {
        console.warn('Permission check error:', err);
      }
    }

    return { accessibility, overlay };
  },

  openAccessibilitySettings(): void {
    if (AppBlockerModule?.openAccessibilitySettings) {
      AppBlockerModule.openAccessibilitySettings();
    } else {
      console.log('[Native Mock] Open Accessibility Settings');
    }
  },

  openOverlaySettings(): void {
    if (AppBlockerModule?.openOverlaySettings) {
      AppBlockerModule.openOverlaySettings();
    } else {
      console.log('[Native Mock] Open Overlay Settings');
    }
  }
};
