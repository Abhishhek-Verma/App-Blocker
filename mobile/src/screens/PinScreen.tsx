import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  TextInput,
  Switch,
  Alert
} from 'react-native';
import { NativeBlockerService } from '../services/nativeBlocker';
import { ApiService } from '../services/api';
import { AppSettings } from '../types';

interface PinScreenProps {
  onBack: () => void;
}

export const PinScreen: React.FC<PinScreenProps> = ({ onBack }) => {
  const [requirePin, setRequirePin] = useState<boolean>(false);
  const [currentPin, setCurrentPin] = useState<string>('');
  const [newPin, setNewPin] = useState<string>('');
  const [testPin, setTestPin] = useState<string>('');
  const [hasPinConfigured, setHasPinConfigured] = useState<boolean>(false);
  const [statusMessage, setStatusMessage] = useState<string>('');

  useEffect(() => {
    loadSettings();
  }, []);

  const loadSettings = async () => {
    const settings = await ApiService.getSettings();
    if (settings) {
      setRequirePin(settings.require_pin_to_unlock);
      setHasPinConfigured(settings.has_pin);
    }
  };

  const togglePinMode = async (value: boolean) => {
    setRequirePin(value);
    await NativeBlockerService.setPinCode(currentPin || null, value);
    await ApiService.updateSettings({ require_pin_to_unlock: value });
    setStatusMessage(value ? 'PIN requirement enabled' : 'Direct mode enabled (No PIN required)');
  };

  const handleSavePin = async () => {
    if (newPin.length !== 4) {
      setStatusMessage('PIN must be exactly 4 digits');
      return;
    }

    setCurrentPin(newPin);
    setHasPinConfigured(true);
    setRequirePin(true);

    await NativeBlockerService.setPinCode(newPin, true);
    await ApiService.updateSettings({
      pin_code: newPin,
      require_pin_to_unlock: true
    });

    setNewPin('');
    setStatusMessage('New 4-digit PIN saved successfully!');
  };

  const handleTestVerify = async () => {
    const isCorrect = await ApiService.verifyPin(testPin);
    if (isCorrect) {
      setStatusMessage('PIN verified successfully! Unlocked.');
    } else {
      setStatusMessage('Incorrect PIN entered.');
    }
    setTestPin('');
  };

  return (
    <View style={styles.container}>
      <View style={styles.topBar}>
        <TouchableOpacity onPress={onBack} style={styles.backButton}>
          <Text style={styles.backButtonText}>← Back</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Security & PIN</Text>
      </View>

      <View style={styles.content}>
        {/* Mode Selector Card */}
        <View style={styles.card}>
          <View style={styles.row}>
            <View style={{ flex: 1 }}>
              <Text style={styles.cardTitle}>Direct Mode vs PIN Protection</Text>
              <Text style={styles.cardSubtitle}>
                {requirePin
                  ? 'PIN Mode: Entering a 4-digit PIN is required to unblock apps or bypass interception.'
                  : 'Direct Mode: No PIN required. 1-tap toggling in the app blocks/unblocks instantly.'}
              </Text>
            </View>
            <Switch
              value={requirePin}
              onValueChange={togglePinMode}
              trackColor={{ false: '#374151', true: '#4F46E5' }}
              thumbColor={requirePin ? '#818CF8' : '#9CA3AF'}
            />
          </View>
        </View>

        {/* Set / Update PIN */}
        <View style={styles.card}>
          <Text style={styles.cardTitle}>
            {hasPinConfigured ? 'Change 4-Digit PIN' : 'Set a Simple 4-Digit PIN'}
          </Text>
          <Text style={styles.cardSubtitle}>
            Configure a passcode to prevent yourself from easily bypassing blocked apps.
          </Text>

          <View style={styles.pinInputRow}>
            <TextInput
              style={styles.pinInput}
              placeholder="••••"
              placeholderTextColor="#6B7280"
              keyboardType="numeric"
              maxLength={4}
              secureTextEntry
              value={newPin}
              onChangeText={setNewPin}
            />
            <TouchableOpacity style={styles.saveButton} onPress={handleSavePin}>
              <Text style={styles.saveButtonText}>Save PIN</Text>
            </TouchableOpacity>
          </View>
        </View>

        {/* Test Verification */}
        {hasPinConfigured && (
          <View style={styles.card}>
            <Text style={styles.cardTitle}>Test Unlock Verification</Text>
            <Text style={styles.cardSubtitle}>Verify your PIN works as expected.</Text>

            <View style={styles.pinInputRow}>
              <TextInput
                style={styles.pinInput}
                placeholder="Enter PIN"
                placeholderTextColor="#6B7280"
                keyboardType="numeric"
                maxLength={4}
                secureTextEntry
                value={testPin}
                onChangeText={setTestPin}
              />
              <TouchableOpacity style={styles.testButton} onPress={handleTestVerify}>
                <Text style={styles.testButtonText}>Test</Text>
              </TouchableOpacity>
            </View>
          </View>
        )}

        {/* Status Message Feedback */}
        {statusMessage ? (
          <View style={styles.statusBox}>
            <Text style={styles.statusText}>{statusMessage}</Text>
          </View>
        ) : null}
      </View>
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
  content: {
    padding: 20
  },
  card: {
    backgroundColor: '#141824',
    borderRadius: 16,
    padding: 18,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#232A3B'
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center'
  },
  cardTitle: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '700',
    marginBottom: 4
  },
  cardSubtitle: {
    color: '#9CA3AF',
    fontSize: 13,
    lineHeight: 18
  },
  pinInputRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 16
  },
  pinInput: {
    flex: 1,
    backgroundColor: '#0F131C',
    borderWidth: 1,
    borderColor: '#2D3748',
    borderRadius: 12,
    paddingVertical: 12,
    paddingHorizontal: 16,
    color: '#FFFFFF',
    fontSize: 18,
    letterSpacing: 4,
    textAlign: 'center',
    marginRight: 12
  },
  saveButton: {
    backgroundColor: '#4F46E5',
    paddingVertical: 14,
    paddingHorizontal: 20,
    borderRadius: 12
  },
  saveButtonText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '700'
  },
  testButton: {
    backgroundColor: '#1E2433',
    borderWidth: 1,
    borderColor: '#4F46E5',
    paddingVertical: 14,
    paddingHorizontal: 20,
    borderRadius: 12
  },
  testButtonText: {
    color: '#818CF8',
    fontSize: 14,
    fontWeight: '700'
  },
  statusBox: {
    backgroundColor: '#1E2433',
    padding: 14,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#374151',
    alignItems: 'center',
    marginTop: 10
  },
  statusText: {
    color: '#A5B4FC',
    fontSize: 13,
    fontWeight: '600'
  }
});
