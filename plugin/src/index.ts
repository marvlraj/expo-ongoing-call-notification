import {
  ConfigPlugin,
  withAndroidManifest,
  withInfoPlist,
  withStringsXml,
  AndroidConfig,
  createRunOncePlugin,
} from '@expo/config-plugins';

export interface OngoingCallPluginProps {
  scheme?: string;
  defaultCallName?: string;
  enableVoipBackgroundMode?: boolean;
}

const withOngoingCallNotificationAndroid: ConfigPlugin<OngoingCallPluginProps | void> = (
  config,
  props
) => {
  // 1. Android Manifest permissions & services
  config = withAndroidManifest(config, (config) => {
    const androidManifest = config.modResults;

    // Permissions required for notifications, lockscreen, audio, and foreground service
    AndroidConfig.Permissions.addPermission(androidManifest, 'android.permission.FOREGROUND_SERVICE');
    AndroidConfig.Permissions.addPermission(androidManifest, 'android.permission.FOREGROUND_SERVICE_MICROPHONE');
    AndroidConfig.Permissions.addPermission(androidManifest, 'android.permission.FOREGROUND_SERVICE_PHONE_CALL');
    AndroidConfig.Permissions.addPermission(androidManifest, 'android.permission.POST_NOTIFICATIONS');
    AndroidConfig.Permissions.addPermission(androidManifest, 'android.permission.USE_FULL_SCREEN_INTENT');
    AndroidConfig.Permissions.addPermission(androidManifest, 'android.permission.MANAGE_OWN_CALLS');

    const mainApplication = AndroidConfig.Manifest.getMainApplicationOrThrow(androidManifest);

    const service = {
      $: {
        'android:name': 'expo.modules.ongoingcallnotification.OngoingCallService',
        'android:foregroundServiceType': 'microphone|phoneCall',
        'android:exported': 'false' as const,
      },
    };

    const connectionService = {
      $: {
        'android:name': 'expo.modules.ongoingcallnotification.VoipConnectionService',
        'android:permission': 'android.permission.BIND_TELECOM_CONNECTION_SERVICE',
        'android:exported': 'true' as const,
      },
      'intent-filter': [
        {
          action: [
            {
              $: {
                'android:name': 'android.telecom.ConnectionService',
              },
            },
          ],
        },
      ],
    };

    if (!mainApplication.service) {
      mainApplication.service = [];
    }

    // Register or update OngoingCallService
    const existingServiceIndex = mainApplication.service.findIndex(
      (s: any) => s.$['android:name'] === service.$['android:name']
    );

    if (existingServiceIndex > -1) {
      mainApplication.service[existingServiceIndex] = service;
    } else {
      mainApplication.service.push(service);
    }

    // Register or update VoipConnectionService
    const existingConnectionServiceIndex = mainApplication.service.findIndex(
      (s: any) => s.$['android:name'] === connectionService.$['android:name']
    );

    if (existingConnectionServiceIndex > -1) {
      mainApplication.service[existingConnectionServiceIndex] = connectionService;
    } else {
      mainApplication.service.push(connectionService);
    }

    return config;
  });

  // 2. Android strings.xml customization (scheme and call label)
  config = withStringsXml(config, (config) => {
    const strings = config.modResults;
    if (props?.scheme) {
      AndroidConfig.Strings.setStringItem(
        [{ $: { name: 'ongoing_call_scheme' }, _: props.scheme }],
        strings
      );
    }
    if (props?.defaultCallName) {
      AndroidConfig.Strings.setStringItem(
        [{ $: { name: 'ongoing_call_default_name' }, _: props.defaultCallName }],
        strings
      );
    }
    return config;
  });

  return config;
};

const withOngoingCallNotificationIOS: ConfigPlugin<OngoingCallPluginProps | void> = (
  config,
  props
) => {
  return withInfoPlist(config, (config) => {
    if (props?.enableVoipBackgroundMode) {
      const infoPlist = config.modResults;
      if (!Array.isArray(infoPlist.UIBackgroundModes)) {
        infoPlist.UIBackgroundModes = [];
      }
      if (!infoPlist.UIBackgroundModes.includes('voip')) {
        infoPlist.UIBackgroundModes.push('voip');
      }
      if (!infoPlist.UIBackgroundModes.includes('audio')) {
        infoPlist.UIBackgroundModes.push('audio');
      }
    }
    return config;
  });
};

const withOngoingCallNotification: ConfigPlugin<OngoingCallPluginProps | void> = (
  config,
  props
) => {
  config = withOngoingCallNotificationAndroid(config, props);
  config = withOngoingCallNotificationIOS(config, props);
  return config;
};

const pkg = { name: 'expo-ongoing-call-notification', version: '1.0.0' };

export default createRunOncePlugin(
  withOngoingCallNotification,
  pkg.name,
  pkg.version
);
