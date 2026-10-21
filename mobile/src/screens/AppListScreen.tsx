import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TextInput,
  Switch,
  TouchableOpacity,
  ActivityIndicator
} from 'react-native';
import { NativeBlockerService } from '../services/nativeBlocker';
import { ApiService } from '../services/api';
import { AppItem } from '../types';

interface AppListScreenProps {
  onBack: () => void;
}

export const AppListScreen: React.FC<AppListScreenProps> = ({ onBack }) => {
  const [apps, setApps] = useState<AppItem[]>([]);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(true);
  const [activeTab, setActiveTab] = useState<'all' | 'blocked'>('all');

  useEffect(() => {
    loadInstalledApps();
  }, []);

  const loadInstalledApps = async () => {
    setLoading(true);
    const installed = await NativeBlockerService.getInstalledApps();
    setApps(installed);
    setLoading(false);
  };

  const toggleAppBlock = async (item: AppItem) => {
    const newBlockedState = !item.isBlocked;

    // Update local UI immediately
    const updatedApps = apps.map(a =>
      a.packageName === item.packageName ? { ...a, isBlocked: newBlockedState } : a
    );
    setApps(updatedApps);

    // Update native SharedPreferences fast cache (<1ms response time)
    const blockedList = updatedApps.filter(a => a.isBlocked).map(a => a.packageName);
    await NativeBlockerService.setBlockedPackages(blockedList);

    // Sync to PostgreSQL backend
    await ApiService.toggleBlockedApp(item.packageName, item.appName, newBlockedState);
  };

  const filteredApps = apps.filter(app => {
    const matchesSearch =
      app.appName.toLowerCase().includes(searchQuery.toLowerCase()) ||
      app.packageName.toLowerCase().includes(searchQuery.toLowerCase());
    if (activeTab === 'blocked') {
      return matchesSearch && app.isBlocked;
    }
    return matchesSearch;
  });

  const renderAppItem = ({ item }: { item: AppItem }) => (
    <View style={styles.appRow}>
      <View style={styles.appIconPlaceholder}>
        <Text style={styles.appIconText}>
          {item.appName.charAt(0).toUpperCase()}
        </Text>
      </View>
      <View style={styles.appInfo}>
        <Text style={styles.appName} numberOfLines={1}>
          {item.appName}
        </Text>
        <Text style={styles.appPackage} numberOfLines={1}>
          {item.packageName}
        </Text>
      </View>
      <Switch
        value={item.isBlocked}
        onValueChange={() => toggleAppBlock(item)}
        trackColor={{ false: '#2D3748', true: '#EF4444' }}
        thumbColor={item.isBlocked ? '#FFFFFF' : '#9CA3AF'}
      />
    </View>
  );

  return (
    <View style={styles.container}>
      {/* Top Bar */}
      <View style={styles.topBar}>
        <TouchableOpacity onPress={onBack} style={styles.backButton}>
          <Text style={styles.backButtonText}>← Back</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Select Apps to Block</Text>
      </View>

      {/* Search Input */}
      <View style={styles.searchContainer}>
        <TextInput
          style={styles.searchInput}
          placeholder="Search apps by name or package..."
          placeholderTextColor="#6B7280"
          value={searchQuery}
          onChangeText={setSearchQuery}
        />
      </View>

      {/* Tabs */}
      <View style={styles.tabsRow}>
        <TouchableOpacity
          style={[styles.tab, activeTab === 'all' && styles.tabActive]}
          onPress={() => setActiveTab('all')}
        >
          <Text style={[styles.tabText, activeTab === 'all' && styles.tabTextActive]}>
            All Apps ({apps.length})
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.tab, activeTab === 'blocked' && styles.tabActive]}
          onPress={() => setActiveTab('blocked')}
        >
          <Text style={[styles.tabText, activeTab === 'blocked' && styles.tabTextActive]}>
            Blocked ({apps.filter(a => a.isBlocked).length})
          </Text>
        </TouchableOpacity>
      </View>

      {/* Apps List */}
      {loading ? (
        <View style={styles.loaderCenter}>
          <ActivityIndicator size="large" color="#6366F1" />
          <Text style={styles.loadingText}>Scanning device packages...</Text>
        </View>
      ) : (
        <FlatList
          data={filteredApps}
          keyExtractor={item => item.packageName}
          renderItem={renderAppItem}
          contentContainerStyle={styles.listContent}
          ListEmptyComponent={
            <View style={styles.emptyContainer}>
              <Text style={styles.emptyText}>No apps found matching your search.</Text>
            </View>
          }
        />
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0A0D14'
  },
  topBar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingTop: 16,
    paddingBottom: 12
  },
  backButton: {
    paddingVertical: 6,
    paddingHorizontal: 12,
    backgroundColor: '#1E2433',
    borderRadius: 8,
    marginRight: 14
  },
  backButtonText: {
    color: '#818CF8',
    fontSize: 14,
    fontWeight: '700'
  },
  headerTitle: {
    color: '#FFFFFF',
    fontSize: 20,
    fontWeight: '700'
  },
  searchContainer: {
    paddingHorizontal: 20,
    marginVertical: 10
  },
  searchInput: {
    backgroundColor: '#161B26',
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 12,
    color: '#FFFFFF',
    fontSize: 14,
    borderWidth: 1,
    borderColor: '#2A3347'
  },
  tabsRow: {
    flexDirection: 'row',
    paddingHorizontal: 20,
    marginBottom: 10
  },
  tab: {
    paddingVertical: 8,
    paddingHorizontal: 14,
    borderRadius: 10,
    marginRight: 10,
    backgroundColor: '#161922'
  },
  tabActive: {
    backgroundColor: '#312E81'
  },
  tabText: {
    color: '#9CA3AF',
    fontSize: 13,
    fontWeight: '600'
  },
  tabTextActive: {
    color: '#E0E7FF'
  },
  listContent: {
    paddingHorizontal: 20,
    paddingBottom: 40
  },
  appRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#141824',
    padding: 14,
    borderRadius: 14,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: '#232A3B'
  },
  appIconPlaceholder: {
    width: 40,
    height: 40,
    borderRadius: 10,
    backgroundColor: '#252F47',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12
  },
  appIconText: {
    color: '#818CF8',
    fontSize: 18,
    fontWeight: 'bold'
  },
  appInfo: {
    flex: 1
  },
  appName: {
    color: '#FFFFFF',
    fontSize: 15,
    fontWeight: '600'
  },
  appPackage: {
    color: '#6B7280',
    fontSize: 11,
    marginTop: 2
  },
  loaderCenter: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center'
  },
  loadingText: {
    color: '#9CA3AF',
    fontSize: 14,
    marginTop: 12
  },
  emptyContainer: {
    paddingTop: 60,
    alignItems: 'center'
  },
  emptyText: {
    color: '#6B7280',
    fontSize: 14
  }
});
