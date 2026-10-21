import React, { useState, useEffect } from 'react';
import {
  SafeAreaView,
  StatusBar,
  StyleSheet,
  View,
  Text,
  TouchableOpacity
} from 'react-native';
import { HomeScreen } from './screens/HomeScreen';
import { AppListScreen } from './screens/AppListScreen';
import { PinScreen } from './screens/PinScreen';
import { PermissionsGuideScreen } from './screens/PermissionsGuideScreen';
import { NativeBlockerService } from './services/nativeBlocker';
import { ApiService } from './services/api';

type ScreenType = 'home' | 'apps' | 'security' | 'permissions';

export default function App() {
  const [currentScreen, setCurrentScreen] = useState<ScreenType>('home');

  useEffect(() => {
    // Sync PostgreSQL backend state with Android native SharedPreferences on launch
    const syncOnStartup = async () => {
      try {
        const backendApps = await ApiService.getBlockedApps();
        if (backendApps && backendApps.length > 0) {
          const blockedPackages = backendApps
            .filter((a: any) => a.is_blocked)
            .map((a: any) => a.package_name);
          await NativeBlockerService.setBlockedPackages(blockedPackages);
        }

        const settings = await ApiService.getSettings();
        if (settings) {
          await NativeBlockerService.setMasterActive(settings.master_blocker_active);
        }
      } catch (err) {
        console.warn('Initial sync warning:', err);
      }
    };

    syncOnStartup();
  }, []);

  const renderCurrentScreen = () => {
    switch (currentScreen) {
      case 'home':
        return (
          <HomeScreen
            onNavigateToApps={() => setCurrentScreen('apps')}
            onNavigateToPermissions={() => setCurrentScreen('permissions')}
            onNavigateToPin={() => setCurrentScreen('security')}
          />
        );
      case 'apps':
        return <AppListScreen onBack={() => setCurrentScreen('home')} />;
      case 'security':
        return <PinScreen onBack={() => setCurrentScreen('home')} />;
      case 'permissions':
        return <PermissionsGuideScreen onBack={() => setCurrentScreen('home')} />;
      default:
        return (
          <HomeScreen
            onNavigateToApps={() => setCurrentScreen('apps')}
            onNavigateToPermissions={() => setCurrentScreen('permissions')}
            onNavigateToPin={() => setCurrentScreen('security')}
          />
        );
    }
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="light-content" backgroundColor="#0A0D14" />
      <View style={styles.container}>{renderCurrentScreen()}</View>

      {/* Bottom Navigation Bar */}
      <View style={styles.bottomNav}>
        <TouchableOpacity
          style={[styles.navItem, currentScreen === 'home' && styles.navItemActive]}
          onPress={() => setCurrentScreen('home')}
        >
          <Text style={styles.navIcon}>🛡️</Text>
          <Text style={[styles.navLabel, currentScreen === 'home' && styles.navLabelActive]}>
            Shield
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.navItem, currentScreen === 'apps' && styles.navItemActive]}
          onPress={() => setCurrentScreen('apps')}
        >
          <Text style={styles.navIcon}>📱</Text>
          <Text style={[styles.navLabel, currentScreen === 'apps' && styles.navLabelActive]}>
            Apps
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.navItem, currentScreen === 'security' && styles.navItemActive]}
          onPress={() => setCurrentScreen('security')}
        >
          <Text style={styles.navIcon}>🔒</Text>
          <Text style={[styles.navLabel, currentScreen === 'security' && styles.navLabelActive]}>
            PIN
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.navItem, currentScreen === 'permissions' && styles.navItemActive]}
          onPress={() => setCurrentScreen('permissions')}
        >
          <Text style={styles.navIcon}>⚡</Text>
          <Text style={[styles.navLabel, currentScreen === 'permissions' && styles.navLabelActive]}>
            Permissions
          </Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#0A0D14'
  },
  container: {
    flex: 1
  },
  bottomNav: {
    flexDirection: 'row',
    backgroundColor: '#0D111A',
    borderTopWidth: 1,
    borderTopColor: '#1F2937',
    paddingVertical: 10,
    paddingHorizontal: 8
  },
  navItem: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 6,
    borderRadius: 12
  },
  navItemActive: {
    backgroundColor: '#161E2E'
  },
  navIcon: {
    fontSize: 20
  },
  navLabel: {
    color: '#9CA3AF',
    fontSize: 11,
    fontWeight: '600',
    marginTop: 3
  },
  navLabelActive: {
    color: '#818CF8',
    fontWeight: '700'
  }
});
