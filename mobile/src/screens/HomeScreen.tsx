import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Switch,
  ScrollView,
  RefreshControl
} from 'react-native';
import { NativeBlockerService } from '../services/nativeBlocker';
import { ApiService } from '../services/api';
import { AppItem, BlockStats, PermissionStatus } from '../types';

interface HomeScreenProps {
  onNavigateToApps: () => void;
  onNavigateToPermissions: () => void;
  onNavigateToPin: () => void;
}

export const HomeScreen: React.FC<HomeScreenProps> = ({
  onNavigateToApps,
  onNavigateToPermissions,
  onNavigateToPin
}) => {
  const [masterActive, setMasterActive] = useState<boolean>(true);
  const [isInstagramBlocked, setIsInstagramBlocked] = useState<boolean>(true);
  const [permissions, setPermissions] = useState<PermissionStatus>({ accessibility: false, overlay: false });
  const [stats, setStats] = useState<BlockStats>({ blocked_today: 0, blocked_total: 0, top_blocked: [] });
  const [refreshing, setRefreshing] = useState<boolean>(false);

  const loadData = async () => {
    // 1. Check permissions
    const perms = await NativeBlockerService.checkPermissions();
    setPermissions(perms);

    // 2. Check blocked list for Instagram
    const blocked = await NativeBlockerService.getBlockedPackages();
    setIsInstagramBlocked(blocked.includes('com.instagram.android'));

    // 3. Load stats from backend
    const apiStats = await ApiService.getStats();
    setStats(apiStats);
  };

  useEffect(() => {
    loadData();
  }, []);

  const onRefresh = async () => {
    setRefreshing(true);
    await loadData();
    setRefreshing(false);
  };

  const toggleMasterShield = async (value: boolean) => {
    setMasterActive(value);
    await NativeBlockerService.setMasterActive(value);
    await ApiService.updateSettings({ master_blocker_active: value });
  };

  const toggleInstagram = async () => {
    const newState = !isInstagramBlocked;
    setIsInstagramBlocked(newState);

    const currentBlocked = await NativeBlockerService.getBlockedPackages();
    let updated: string[];
    if (newState) {
      updated = Array.from(new Set([...currentBlocked, 'com.instagram.android']));
    } else {
      updated = currentBlocked.filter(pkg => pkg !== 'com.instagram.android');
    }

    await NativeBlockerService.setBlockedPackages(updated);
    await ApiService.toggleBlockedApp('com.instagram.android', 'Instagram', newState);
  };

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.contentContainer}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#6366F1" />}
    >
      {/* App Header */}
      <View style={styles.header}>
        <View>
          <Text style={styles.appTitle}>FocusShield</Text>
          <Text style={styles.appSubtitle}>Real-time App Interceptor</Text>
        </View>
        <TouchableOpacity style={styles.settingsBadge} onPress={onNavigateToPin}>
          <Text style={styles.settingsBadgeText}>⚙️ Security</Text>
        </TouchableOpacity>
      </View>

      {/* Permissions Warning Banner if any permission missing */}
      {(!permissions.accessibility || !permissions.overlay) && (
        <TouchableOpacity style={styles.warningCard} onPress={onNavigateToPermissions}>
          <Text style={styles.warningIcon}>⚠️</Text>
          <View style={styles.warningTextContainer}>
            <Text style={styles.warningTitle}>Setup Required</Text>
            <Text style={styles.warningSubtitle}>
              Tap to grant {!permissions.accessibility ? 'Accessibility' : 'Overlay'} permission to block apps in real time.
            </Text>
          </View>
          <Text style={styles.warningArrow}>→</Text>
        </TouchableOpacity>
      )}

      {/* Master Blocker Shield Switch */}
      <View style={[styles.card, masterActive ? styles.cardActive : styles.cardInactive]}>
        <View style={styles.cardHeader}>
          <View>
            <Text style={styles.cardLabel}>SYSTEM SHIELD</Text>
            <Text style={styles.cardState}>{masterActive ? 'ACTIVE & BLOCKING' : 'PAUSED'}</Text>
          </View>
          <Switch
            value={masterActive}
            onValueChange={toggleMasterShield}
            trackColor={{ false: '#374151', true: '#4F46E5' }}
            thumbColor={masterActive ? '#818CF8' : '#9CA3AF'}
          />
        </View>
        <Text style={styles.cardDescription}>
          {masterActive
            ? 'Blocked apps will be intercepted immediately before they can open.'
            : 'Shield is paused. All apps can be opened normally.'}
        </Text>
      </View>

      {/* One-Tap Instagram Blocker Hero Card */}
      <View style={styles.heroCard}>
        <View style={styles.heroRow}>
          <View style={styles.heroIconBadge}>
            <Text style={styles.heroIcon}>📸</Text>
          </View>
          <View style={styles.heroTextContainer}>
            <Text style={styles.heroTitle}>Instagram</Text>
            <Text style={styles.heroPackage}>com.instagram.android</Text>
          </View>
          <View style={[styles.statusPill, isInstagramBlocked ? styles.pillBlocked : styles.pillAllowed]}>
            <Text style={styles.statusPillText}>
              {isInstagramBlocked ? 'BLOCKED' : 'ALLOWED'}
            </Text>
          </View>
        </View>

        <Text style={styles.heroExplanation}>
          {isInstagramBlocked
            ? 'When you tap Instagram, a popup appears: "Instagram: No, you cannot open it" and returns you to Home.'
            : 'Instagram is unblocked. You can open and use it freely.'}
        </Text>

        <TouchableOpacity
          style={[styles.heroButton, isInstagramBlocked ? styles.heroButtonUnblock : styles.heroButtonBlock]}
          onPress={toggleInstagram}
        >
          <Text style={styles.heroButtonText}>
            {isInstagramBlocked ? 'Unblock Instagram' : 'Block Instagram Now'}
          </Text>
        </TouchableOpacity>
      </View>

      {/* Daily Stats Grid */}
      <View style={styles.statsRow}>
        <View style={styles.statBox}>
          <Text style={styles.statValue}>{stats.blocked_today}</Text>
          <Text style={styles.statLabel}>Attempts Blocked Today</Text>
        </View>
        <View style={styles.statBox}>
          <Text style={styles.statValue}>{stats.blocked_total}</Text>
          <Text style={styles.statLabel}>Total Interceptions</Text>
        </View>
      </View>

      {/* Manage More Apps Button */}
      <TouchableOpacity style={styles.actionButton} onPress={onNavigateToApps}>
        <Text style={styles.actionButtonText}>📋 Manage All Installed Apps</Text>
      </TouchableOpacity>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0A0D14'
  },
  contentContainer: {
    padding: 20,
    paddingBottom: 40
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 20
  },
  appTitle: {
    color: '#F9FAFB',
    fontSize: 28,
    fontWeight: '800',
    letterSpacing: -0.5
  },
  appSubtitle: {
    color: '#9CA3AF',
    fontSize: 14,
    marginTop: 2
  },
  settingsBadge: {
    backgroundColor: '#1E2433',
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#2D3748'
  },
  settingsBadgeText: {
    color: '#E2E8F0',
    fontSize: 13,
    fontWeight: '600'
  },
  warningCard: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#3B1812',
    borderWidth: 1,
    borderColor: '#7F1D1D',
    borderRadius: 16,
    padding: 16,
    marginBottom: 20
  },
  warningIcon: {
    fontSize: 24,
    marginRight: 12
  },
  warningTextContainer: {
    flex: 1
  },
  warningTitle: {
    color: '#FCA5A5',
    fontSize: 15,
    fontWeight: '700'
  },
  warningSubtitle: {
    color: '#F87171',
    fontSize: 12,
    marginTop: 2
  },
  warningArrow: {
    color: '#FCA5A5',
    fontSize: 18,
    fontWeight: 'bold',
    marginLeft: 8
  },
  card: {
    borderRadius: 20,
    padding: 20,
    marginBottom: 20,
    borderWidth: 1
  },
  cardActive: {
    backgroundColor: '#131A29',
    borderColor: '#3730A3'
  },
  cardInactive: {
    backgroundColor: '#161922',
    borderColor: '#2D3748'
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center'
  },
  cardLabel: {
    color: '#818CF8',
    fontSize: 12,
    fontWeight: '800',
    letterSpacing: 1
  },
  cardState: {
    color: '#FFFFFF',
    fontSize: 20,
    fontWeight: '700',
    marginTop: 4
  },
  cardDescription: {
    color: '#9CA3AF',
    fontSize: 13,
    marginTop: 12,
    lineHeight: 18
  },
  heroCard: {
    backgroundColor: '#161B26',
    borderRadius: 20,
    padding: 20,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#2A3347'
  },
  heroRow: {
    flexDirection: 'row',
    alignItems: 'center'
  },
  heroIconBadge: {
    width: 48,
    height: 48,
    borderRadius: 14,
    backgroundColor: '#271F36',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 14
  },
  heroIcon: {
    fontSize: 24
  },
  heroTextContainer: {
    flex: 1
  },
  heroTitle: {
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: '700'
  },
  heroPackage: {
    color: '#6B7280',
    fontSize: 12,
    marginTop: 2
  },
  statusPill: {
    paddingVertical: 5,
    paddingHorizontal: 10,
    borderRadius: 8
  },
  pillBlocked: {
    backgroundColor: '#450A0A',
    borderWidth: 1,
    borderColor: '#991B1B'
  },
  pillAllowed: {
    backgroundColor: '#064E3B',
    borderWidth: 1,
    borderColor: '#059669'
  },
  statusPillText: {
    color: '#FFFFFF',
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 0.5
  },
  heroExplanation: {
    color: '#9CA3AF',
    fontSize: 13,
    marginVertical: 14,
    lineHeight: 18
  },
  heroButton: {
    paddingVertical: 14,
    borderRadius: 12,
    alignItems: 'center'
  },
  heroButtonBlock: {
    backgroundColor: '#EF4444'
  },
  heroButtonUnblock: {
    backgroundColor: '#1F2937',
    borderWidth: 1,
    borderColor: '#374151'
  },
  heroButtonText: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '700'
  },
  statsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 20
  },
  statBox: {
    flex: 0.48,
    backgroundColor: '#141824',
    padding: 16,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#232A3B'
  },
  statValue: {
    color: '#818CF8',
    fontSize: 28,
    fontWeight: '800'
  },
  statLabel: {
    color: '#9CA3AF',
    fontSize: 12,
    marginTop: 4
  },
  actionButton: {
    backgroundColor: '#1E2433',
    paddingVertical: 16,
    borderRadius: 16,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#374151'
  },
  actionButtonText: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '600'
  }
});
