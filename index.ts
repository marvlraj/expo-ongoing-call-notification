import { Subscription } from 'expo-modules-core';
import OngoingCallNotificationModule from './OngoingCallNotificationModule';

export type EventSubscription = Subscription;

export interface CallNotificationActionEvent {
  chatId?: string;
  callerId?: string;
}

export function startOngoingCallNotification(callerName: string, avatarUrl?: string | null): void {
  return OngoingCallNotificationModule.startOngoingCallNotification(callerName, avatarUrl || null);
}

export function startIncomingCallNotification(
  callerName: string,
  avatarUrl?: string | null,
  chatId?: string | null,
  callerId?: string | null
): void {
  return OngoingCallNotificationModule.startIncomingCallNotification(
    callerName,
    avatarUrl || null,
    chatId || null,
    callerId || null
  );
}

export function stopOngoingCallNotification(): void {
  return OngoingCallNotificationModule.stopOngoingCallNotification();
}

export function addHangUpListener(listener: (event?: CallNotificationActionEvent) => void): Subscription {
  return OngoingCallNotificationModule.addListener('onHangUp', listener);
}

export function addAnswerListener(listener: (event?: CallNotificationActionEvent) => void): Subscription {
  return OngoingCallNotificationModule.addListener('onAnswer', listener);
}

export function addDeclineListener(listener: (event?: CallNotificationActionEvent) => void): Subscription {
  return OngoingCallNotificationModule.addListener('onDecline', listener);
}

export function onRingingScreenMounted(): void {
  return OngoingCallNotificationModule.onRingingScreenMounted?.();
}
