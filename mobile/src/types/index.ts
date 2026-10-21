export interface AppItem {
  packageName: string;
  appName: string;
  isBlocked: boolean;
  isSystemApp?: boolean;
}

export interface AppSettings {
  device_id: string;
  master_blocker_active: boolean;
  require_pin_to_unlock: boolean;
  has_pin: boolean;
}

export interface BlockStats {
  blocked_today: number;
  blocked_total: number;
  top_blocked: Array<{
    app_name: string;
    package_name: string;
    attempts: number;
  }>;
}

export interface PermissionStatus {
  accessibility: boolean;
  overlay: boolean;
}
