import { Platform } from 'react-native';
import { AppSettings, BlockStats } from '../types';

import { API_CONFIG } from '../config/apiConfig';

const HOST = API_CONFIG.getBaseUrl();

export const ApiService = {
  async getBlockedApps(): Promise<any[]> {
    try {
      const res = await fetch(`${HOST}/api/apps`);
      const data = await res.json();
      return data.success ? data.data : [];
    } catch (e) {
      console.warn('[API] Could not reach backend, operating offline:', e);
      return [];
    }
  },

  async toggleBlockedApp(packageName: string, appName: string, isBlocked: boolean): Promise<any> {
    try {
      const res = await fetch(`${HOST}/api/apps/toggle`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ package_name: packageName, app_name: appName, is_blocked: isBlocked })
      });
      return await res.json();
    } catch (e) {
      console.warn('[API] toggleBlockedApp error:', e);
      return { success: false };
    }
  },

  async getSettings(): Promise<AppSettings | null> {
    try {
      const res = await fetch(`${HOST}/api/settings`);
      const data = await res.json();
      return data.success ? data.data : null;
    } catch (e) {
      console.warn('[API] getSettings error:', e);
      return null;
    }
  },

  async updateSettings(settings: Partial<AppSettings & { pin_code?: string | null }>): Promise<any> {
    try {
      const res = await fetch(`${HOST}/api/settings`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(settings)
      });
      return await res.json();
    } catch (e) {
      console.warn('[API] updateSettings error:', e);
      return { success: false };
    }
  },

  async verifyPin(pin: string): Promise<boolean> {
    try {
      const res = await fetch(`${HOST}/api/settings/verify-pin`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ pin })
      });
      const data = await res.json();
      return Boolean(data.verified);
    } catch (e) {
      console.warn('[API] verifyPin error:', e);
      return false;
    }
  },

  async logBlockEvent(packageName: string, appName: string): Promise<void> {
    try {
      await fetch(`${HOST}/api/events`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ package_name: packageName, app_name: appName, unlocked_via_pin: false })
      });
    } catch (e) {
      // Non-blocking log
    }
  },

  async getStats(): Promise<BlockStats> {
    try {
      const res = await fetch(`${HOST}/api/stats`);
      const data = await res.json();
      if (data.success && data.data) {
        return data.data;
      }
    } catch (e) {
      console.warn('[API] getStats error:', e);
    }
    return {
      blocked_today: 0,
      blocked_total: 0,
      top_blocked: []
    };
  }
};
