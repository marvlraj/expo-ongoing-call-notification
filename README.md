# expo-ongoing-call-notification

[![npm version](https://img.shields.io/npm/v/expo-ongoing-call-notification.svg?style=flat-square)](https://www.npmjs.com/package/expo-ongoing-call-notification)
[![license](https://img.shields.io/npm/l/expo-ongoing-call-notification.svg?style=flat-square)](https://github.com/username/expo-ongoing-call-notification/blob/main/LICENSE)
[![platform](https://img.shields.io/badge/platform-android%20%7C%20ios%20%7C%20web-blue.svg?style=flat-square)](#platform-support)

Enterprise-grade incoming and ongoing VoIP call notifications for **React Native** and **Expo** (Managed & Bare workflows). Engineered specifically for modern Android 14+ background execution rules and iOS CallKit integration.

---

## Highlights & Enterprise Architecture

- 🛡️ **Android 14 (API 34+) Compliant:** Avoids illegal background Foreground Service starts during ringing. Uses direct high-priority `NotificationCompat.CallStyle` with `setFullScreenIntent()`, promoting to a verified `microphone|phoneCall` Foreground Service only when the call is answered.
- 📱 **Lock-Screen Full Screen UI:** Wakes device display and displays full-screen incoming call UI over the lock screen.
- 📞 **Android TelecomManager (`ConnectionService`):** Integrates with Android's native Telecom subsystem for system-level audio routing and Bluetooth headset coordination.
- 🍏 **iOS CallKit Native UI:** Leverages Apple's `CXProvider` and `CXCallController` for native iOS lock screen banners, call controls, and system integration.
- 🌐 **Web Fallback:** Clean no-op stubs prevent SSR and web bundle crashes.
- ⚙️ **Expo Config Plugin:** Automatically configures manifest permissions, background services, schemes, and VoIP entitlements during `npx expo prebuild`.

---

## Platform Support

| Platform | Minimum Version | Notes |
| :--- | :--- | :--- |
| **Android** | Android 8.0 (API 26+) | Tested through Android 14+ (API 34) |
| **iOS** | iOS 15.1+ | Integrates natively with `CallKit` |
| **Expo SDK** | SDK 50+ | Config plugin supports `npx expo prebuild` |

---

## Installation

```bash
npx expo install expo-ongoing-call-notification
```

For bare React Native projects:
```bash
npm install expo-ongoing-call-notification
# For iOS:
npx pod-install
```

---

## Expo Config Plugin Setup (`app.json`)

Add `expo-ongoing-call-notification` to your `plugins` array:

```json
{
  "expo": {
    "name": "My App",
    "slug": "my-app",
    "plugins": [
      [
        "expo-ongoing-call-notification",
        {
          "scheme": "myapp",
          "defaultCallName": "Incoming Call",
          "enableVoipBackgroundMode": true
        }
      ]
    ]
  }
}
```

### Plugin Configuration Options

| Option | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `scheme` | `string` | `"call"` | Custom URL scheme used for deep linking on notification tap (`{scheme}://call` and `{scheme}://incoming`). |
| `defaultCallName` | `string` | App Name | Default caller label shown when no caller name is provided. |
| `enableVoipBackgroundMode` | `boolean` | `false` | Enables `voip` and `audio` in iOS `UIBackgroundModes` within `Info.plist`. |

Then regenerate native projects:
```bash
npx expo prebuild --clean
```

---

## Android Permissions Configured Automatically

The module configures the following permissions via its manifest and config plugin:
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_MICROPHONE`
- `android.permission.FOREGROUND_SERVICE_PHONE_CALL`
- `android.permission.POST_NOTIFICATIONS`
- `android.permission.USE_FULL_SCREEN_INTENT`
- `android.permission.MANAGE_OWN_CALLS`

---

## Complete API Reference

```typescript
import {
  startIncomingCallNotification,
  startOngoingCallNotification,
  stopOngoingCallNotification,
  onRingingScreenMounted,
  addAnswerListener,
  addDeclineListener,
  addHangUpListener,
  CallNotificationActionEvent
} from 'expo-ongoing-call-notification';
```

### 1. `startIncomingCallNotification`
Presents a high-priority incoming call banner with lock-screen full-screen intent and native Answer/Decline buttons.

```typescript
startIncomingCallNotification(
  callerName: string,
  avatarUrl?: string | null,
  chatId?: string | null,
  callerId?: string | null
): void;
```

### 2. `startOngoingCallNotification`
Starts the active call Foreground Service with persistent notification, call timer, and native Hang Up action button.

```typescript
startOngoingCallNotification(
  callerName: string,
  avatarUrl?: string | null
): void;
```

### 3. `stopOngoingCallNotification`
Cancels all active notifications and stops the background Foreground Service.

```typescript
stopOngoingCallNotification(): void;
```

### 4. `onRingingScreenMounted`
Notifies the native module that the React Native full-screen ringing UI has mounted, safely demoting the incoming notification heads-up priority.

```typescript
onRingingScreenMounted(): void;
```

### 5. Event Listeners

#### `addAnswerListener(callback)`
Subscribes to native **Answer** button taps from notification or lock-screen.

```typescript
const sub = addAnswerListener((event?: CallNotificationActionEvent) => {
  console.log('Answered call for chatId:', event?.chatId);
});
sub.remove();
```

#### `addDeclineListener(callback)`
Subscribes to native **Decline** button taps.

```typescript
const sub = addDeclineListener((event?: CallNotificationActionEvent) => {
  console.log('Declined call for chatId:', event?.chatId);
});
sub.remove();
```

#### `addHangUpListener(callback)`
Subscribes to native **Hang Up** button taps from the ongoing call notification.

```typescript
const sub = addHangUpListener((event?: CallNotificationActionEvent) => {
  console.log('Call ended via notification hangup');
});
sub.remove();
```

---

## Usage Example

```typescript
import React, { useEffect } from 'react';
import { View, Text, Button } from 'react-native';
import {
  startIncomingCallNotification,
  startOngoingCallNotification,
  stopOngoingCallNotification,
  addAnswerListener,
  addDeclineListener,
  addHangUpListener
} from 'expo-ongoing-call-notification';

export default function CallScreen() {
  useEffect(() => {
    // 1. Listen for user interactions from notification buttons
    const answerSub = addAnswerListener((event) => {
      console.log('User tapped Answer:', event?.chatId);
      // Promote to ongoing active call notification
      startOngoingCallNotification('Alice', 'https://example.com/avatar.png');
    });

    const declineSub = addDeclineListener((event) => {
      console.log('User tapped Decline');
      stopOngoingCallNotification();
    });

    const hangUpSub = addHangUpListener(() => {
      console.log('User tapped Hang Up');
      stopOngoingCallNotification();
    });

    return () => {
      answerSub.remove();
      declineSub.remove();
      hangUpSub.remove();
    };
  }, []);

  const handleSimulateIncoming = () => {
    startIncomingCallNotification(
      'Alice',
      'https://example.com/avatar.png',
      'chat-987',
      'user-123'
    );
  };

  const handleEndCall = () => {
    stopOngoingCallNotification();
  };

  return (
    <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
      <Button title="Simulate Incoming Call" onPress={handleSimulateIncoming} />
      <Button title="End Call" onPress={handleEndCall} />
    </View>
  );
}
```

---

## Customizing Assets on Android

You can override default notification assets by placing matching resource files into your Android app directory (`android/app/src/main/res/`):

- **Small Icon:** `drawable/ic_ongoing_call_notification.xml` (or `.png`). If not provided, it falls back to your host app's launcher icon or the default call icon.
- **Action Drawables:** `drawable/ic_hangup.xml`, `drawable/bg_action_button.xml`, `drawable/bg_hangup_button.xml`.

---

## Contributing & Publishing

### Build TypeScript
```bash
npm run build
```

### Run Linter
```bash
npm run lint
```

---

## License

MIT © 2026
