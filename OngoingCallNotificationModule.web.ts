export default {
  startOngoingCallNotification(_callerName: string, _avatarUrl?: string | null): void {},
  startIncomingCallNotification(
    _callerName: string,
    _avatarUrl?: string | null,
    _chatId?: string | null,
    _callerId?: string | null
  ): void {},
  stopOngoingCallNotification(): void {},
  onRingingScreenMounted(): void {},
  addListener(_eventName: string, _listener: (...args: any[]) => void) {
    return {
      remove: () => {},
    };
  },
  removeListeners(_count: number): void {},
};

