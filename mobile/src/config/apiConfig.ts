import { Platform } from 'react-native';

/**
 * Configure your backend API endpoint here.
 * - For local Android Emulator testing: 'http://10.0.2.2:5000'
 * - For local iOS Simulator or Web: 'http://127.0.0.1:5000'
 * - For deployed Render backend: 'https://<your-render-service-name>.onrender.com'
 */
export const API_CONFIG = {
  // Live Render backend URL
  PRODUCTION_BACKEND_URL: 'https://app-blocker.onrender.com',

  getBaseUrl(): string {
    if (this.PRODUCTION_BACKEND_URL) {
      return this.PRODUCTION_BACKEND_URL.replace(/\/$/, '');
    }
    return Platform.OS === 'android' ? 'http://10.0.2.2:5000' : 'http://127.0.0.1:5000';
  }
};
