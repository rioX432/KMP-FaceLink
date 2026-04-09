/**
 * KMP-FaceLink React Native Example
 *
 * Demonstrates face tracking with blend shape visualization.
 * Shows horizontal bars for each of the 52 ARKit blend shapes.
 */

import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Platform,
  PermissionsAndroid,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {
  FaceLink,
  BlendShape,
  BLEND_SHAPE_COUNT,
  HEAD_TRANSFORM_COUNT,
} from 'react-native-kmp-facelink';
import type { FaceTrackingData } from 'react-native-kmp-facelink';

// Blend shape names for display (matching BlendShape ordinal order)
const BLEND_SHAPE_NAMES: string[] = Object.keys(BlendShape);

const HEAD_TRANSFORM_LABELS = ['pitch', 'yaw', 'roll', 'posX', 'posY', 'posZ'];

async function requestCameraPermission(): Promise<boolean> {
  if (Platform.OS === 'android') {
    const granted = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.CAMERA,
      {
        title: 'Camera Permission',
        message: 'KMP-FaceLink needs camera access for face tracking.',
        buttonPositive: 'OK',
      },
    );
    return granted === PermissionsAndroid.RESULTS.GRANTED;
  }
  // iOS camera permission is handled by Info.plist
  return true;
}

export default function App() {
  const faceLinkRef = useRef<FaceLink | null>(null);
  const [isTracking, setIsTracking] = useState(false);
  const [faceData, setFaceData] = useState<FaceTrackingData | null>(null);
  const [permissionGranted, setPermissionGranted] = useState(false);
  const animFrameRef = useRef<number>(0);

  // Request camera permission on mount
  useEffect(() => {
    requestCameraPermission().then(setPermissionGranted);
  }, []);

  // Polling loop using requestAnimationFrame
  const pollData = useCallback(() => {
    const fl = faceLinkRef.current;
    if (fl && fl.isTracking()) {
      const data = fl.getLatestFaceData();
      if (data) {
        setFaceData(data);
      }
    }
    animFrameRef.current = requestAnimationFrame(pollData);
  }, []);

  const handleStart = useCallback(() => {
    if (!permissionGranted) return;

    if (!faceLinkRef.current) {
      faceLinkRef.current = new FaceLink({ enableFace: true, enableHands: false });
    }
    faceLinkRef.current.start();
    setIsTracking(true);
    animFrameRef.current = requestAnimationFrame(pollData);
  }, [permissionGranted, pollData]);

  const handleStop = useCallback(() => {
    faceLinkRef.current?.stop();
    cancelAnimationFrame(animFrameRef.current);
    setIsTracking(false);
  }, []);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      cancelAnimationFrame(animFrameRef.current);
      faceLinkRef.current?.dispose();
    };
  }, []);

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>KMP-FaceLink</Text>

      {!permissionGranted && (
        <Text style={styles.warning}>Camera permission required for face tracking.</Text>
      )}

      <View style={styles.controls}>
        <TouchableOpacity
          style={[styles.button, isTracking && styles.buttonDisabled]}
          onPress={handleStart}
          disabled={isTracking || !permissionGranted}
        >
          <Text style={styles.buttonText}>Start</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.button, !isTracking && styles.buttonDisabled]}
          onPress={handleStop}
          disabled={!isTracking}
        >
          <Text style={styles.buttonText}>Stop</Text>
        </TouchableOpacity>
      </View>

      <Text style={styles.status}>
        {isTracking ? 'Tracking active' : 'Tracking stopped'}
        {faceData ? ` — face ${faceData.isTracking ? 'detected' : 'lost'}` : ''}
      </Text>

      {/* Head Transform */}
      {faceData && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Head Transform</Text>
          {HEAD_TRANSFORM_LABELS.map((label, i) => (
            <Text key={label} style={styles.transformText}>
              {label}: {faceData.headTransform[i]?.toFixed(3) ?? '0.000'}
            </Text>
          ))}
        </View>
      )}

      {/* Blend Shape Bars */}
      <ScrollView style={styles.blendShapeList}>
        <Text style={styles.sectionTitle}>Blend Shapes</Text>
        {BLEND_SHAPE_NAMES.map((name, index) => {
          const value = faceData?.blendShapes[index] ?? 0;
          return (
            <View key={name} style={styles.barRow}>
              <Text style={styles.barLabel} numberOfLines={1}>
                {name}
              </Text>
              <View style={styles.barBackground}>
                <View
                  style={[styles.barFill, { width: `${Math.min(value * 100, 100)}%` }]}
                />
              </View>
              <Text style={styles.barValue}>{value.toFixed(2)}</Text>
            </View>
          );
        })}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#1a1a2e',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#e0e0e0',
    textAlign: 'center',
    marginTop: 16,
  },
  warning: {
    color: '#ff6b6b',
    textAlign: 'center',
    marginVertical: 8,
    fontSize: 14,
  },
  controls: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 16,
    marginVertical: 12,
  },
  button: {
    backgroundColor: '#4a90d9',
    paddingHorizontal: 32,
    paddingVertical: 12,
    borderRadius: 8,
    minWidth: 120,
    alignItems: 'center',
  },
  buttonDisabled: {
    backgroundColor: '#3a3a5a',
  },
  buttonText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '600',
  },
  status: {
    color: '#90caf9',
    textAlign: 'center',
    marginBottom: 8,
    fontSize: 14,
  },
  section: {
    paddingHorizontal: 16,
    paddingVertical: 8,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#b0b0d0',
    marginBottom: 8,
    paddingHorizontal: 16,
  },
  transformText: {
    color: '#c0c0e0',
    fontSize: 13,
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
  },
  blendShapeList: {
    flex: 1,
  },
  barRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    marginVertical: 2,
  },
  barLabel: {
    width: 140,
    fontSize: 11,
    color: '#a0a0c0',
  },
  barBackground: {
    flex: 1,
    height: 12,
    backgroundColor: '#2a2a4a',
    borderRadius: 6,
    overflow: 'hidden',
  },
  barFill: {
    height: '100%',
    backgroundColor: '#4a90d9',
    borderRadius: 6,
  },
  barValue: {
    width: 44,
    fontSize: 11,
    color: '#a0a0c0',
    textAlign: 'right',
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
  },
});
