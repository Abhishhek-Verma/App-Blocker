import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView
} from 'react-native';
import { NativeBlockerService } from '../services/nativeBlocker';
import { PermissionStatus } from '../types';

interface PermissionsGuideScreenProps {
  onBack: () => void;
}

export const PermissionsGuideScreen: React.FC<PermissionsGuideScreenProps> = ({ onBack }) => {
  const [permissions, setPermissions] = useState<PermissionStatus>({ accessibility: false, overlay: false });

  const check = async () => {
    const status = await NativeBlockerService.checkPermissions();
    setPermissions(status);
  };

  useEffect(() => {
    check();
  }, []);

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <View style={styles.topBar}>
        <TouchableOpacity onPress={onBack} style={styles.backButton}>
          <Text style={styles.backButtonText}>← Back</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Required Permissions</Text>
      </View>

      <Text style={styles.introText}>
        To block apps like Instagram before they can open, Android requires two specific system capabilities:
      </Text>

      {/* Permission 1: Accessibility */}
      <View style={[styles.card, permissions.accessibility ? styles.cardGranted : styles.cardPending]}>
        <View style={styles.cardHeader}>
          <Text style={styles.cardIcon}>⚡</Text>
          <View style={{ flex: 1 }}>
            <Text style={styles.cardTitle}>1. Accessibility Service</Text>
            <Text style={styles.cardStatus}>
              {permissions.accessibility ? '✓ GRANTED & ACTIVE' : 'PENDING SETUP'}
            </Text>
          </View>
        </View>
        <Text style={styles.cardDescription}>
          Detects the instant Instagram is clicked on your home screen (5–15ms latency) and intercepts the launch before the app loads.
        </Text>
        <TouchableOpacity
          style={[styles.actionBtn, permissions.accessibility ? styles.actionBtnGranted : styles.actionBtnGrant]}
          onPress={() => NativeBlockerService.openAccessibilitySettings()}
        >
          <Text style={styles.actionBtnText}>
            {permissions.accessibility ? 'Re-open Settings' : 'Enable in Accessibility Settings'}
          </Text>
        </TouchableOpacity>
      </View>

      {/* Permission 2: Draw Over Other Apps */}
      <View style={[styles.card, permissions.overlay ? styles.cardGranted : styles.cardPending]}>
        <View style={styles.cardHeader}>
          <Text style={styles.cardIcon}>🛡️</Text>
          <View style={{ flex: 1 }}>
            <Text style={styles.cardTitle}>2. Display Over Other Apps</Text>
            <Text style={styles.cardStatus}>
              {permissions.overlay ? '✓ GRANTED & ACTIVE' : 'PENDING SETUP'}
            </Text>
          </View>
        </View>
        <Text style={styles.cardDescription}>
          Allows FocusShield to pop up the blocking overlay directly over Instagram: "Instagram: No, you cannot open it."
        </Text>
        <TouchableOpacity
          style={[styles.actionBtn, permissions.overlay ? styles.actionBtnGranted : styles.actionBtnGrant]}
          onPress={() => NativeBlockerService.openOverlaySettings()}
        >
          <Text style={styles.actionBtnText}>
            {permissions.overlay ? 'Re-open Settings' : 'Allow Display Over Other Apps'}
          </Text>
        </TouchableOpacity>
      </View>

      {/* Refresh Status Button */}
      <TouchableOpacity style={styles.refreshBtn} onPress={check}>
        <Text style={styles.refreshBtnText}>🔄 Re-check Permissions Status</Text>
      </TouchableOpacity>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0A0D14'
  },
  content: {
    padding: 20,
    paddingBottom: 40
  },
  topBar: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 20
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
  introText: {
    color: '#9CA3AF',
    fontSize: 14,
    lineHeight: 20,
    marginBottom: 20
  },
  card: {
    backgroundColor: '#141824',
    borderRadius: 18,
    padding: 20,
    marginBottom: 20,
    borderWidth: 1
  },
  cardPending: {
    borderColor: '#7F1D1D'
  },
  cardGranted: {
    borderColor: '#065F46'
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 10
  },
  cardIcon: {
    fontSize: 28,
    marginRight: 14
  },
  cardTitle: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '700'
  },
  cardStatus: {
    color: '#818CF8',
    fontSize: 12,
    fontWeight: '700',
    marginTop: 2
  },
  cardDescription: {
    color: '#9CA3AF',
    fontSize: 13,
    lineHeight: 18,
    marginBottom: 16
  },
  actionBtn: {
    paddingVertical: 12,
    borderRadius: 12,
    alignItems: 'center'
  },
  actionBtnGrant: {
    backgroundColor: '#4F46E5'
  },
  actionBtnGranted: {
    backgroundColor: '#1E2433',
    borderWidth: 1,
    borderColor: '#374151'
  },
  actionBtnText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '700'
  },
  refreshBtn: {
    backgroundColor: '#161B26',
    borderWidth: 1,
    borderColor: '#2D3748',
    paddingVertical: 14,
    borderRadius: 14,
    alignItems: 'center',
    marginTop: 10
  },
  refreshBtnText: {
    color: '#A5B4FC',
    fontSize: 14,
    fontWeight: '600'
  }
});
