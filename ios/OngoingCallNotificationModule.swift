import ExpoModulesCore
import CallKit

public class OngoingCallNotificationModule: Module, CXProviderDelegate {
  private var provider: CXProvider?
  private var callController = CXCallController()
  private var activeCallUUID: UUID?
  private var currentChatId: String?
  private var currentCallerId: String?

  public func definition() -> ModuleDefinition {
    Name("OngoingCallNotification")

    Events("onHangUp", "onAnswer", "onDecline")

    OnCreate {
      self.setupCallKitProvider()
    }

    OnDestroy {
      self.provider?.invalidate()
      self.provider = nil
    }

    Function("startOngoingCallNotification") { (callerName: String, avatarUrl: String?) in
      let uuid = self.activeCallUUID ?? UUID()
      self.activeCallUUID = uuid

      let update = CXCallUpdate()
      update.remoteHandle = CXHandle(type: .generic, value: callerName)
      update.localizedCallerName = callerName
      update.hasVideo = false
      update.supportsHolding = false
      update.supportsGrouping = false
      update.supportsUngrouping = false
      update.supportsDTMF = false

      self.provider?.reportCall(with: uuid, updated: update)
    }

    Function("startIncomingCallNotification") { (callerName: String, avatarUrl: String?, chatId: String?, callerId: String?) in
      self.currentChatId = chatId
      self.currentCallerId = callerId

      let uuid = UUID()
      self.activeCallUUID = uuid

      let update = CXCallUpdate()
      update.remoteHandle = CXHandle(type: .generic, value: callerName)
      update.localizedCallerName = callerName
      update.hasVideo = false
      update.supportsHolding = false
      update.supportsGrouping = false
      update.supportsUngrouping = false
      update.supportsDTMF = false

      self.provider?.reportNewIncomingCall(with: uuid, update: update) { [weak self] error in
        if let error = error {
          NSLog("[OngoingCallNotification] reportNewIncomingCall error: %@", error.localizedDescription)
          self?.activeCallUUID = nil
        }
      }
    }

    Function("stopOngoingCallNotification") {
      guard let uuid = self.activeCallUUID else { return }
      let endAction = CXEndCallAction(call: uuid)
      let transaction = CXTransaction(action: endAction)

      self.callController.request(transaction) { [weak self] error in
        if let error = error {
          NSLog("[OngoingCallNotification] stopOngoingCallNotification error: %@", error.localizedDescription)
          // Fallback: report call ended directly via provider if transaction failed
          self?.provider?.reportCall(with: uuid, endedAt: Date(), reason: .remoteEnded)
        }
        self?.activeCallUUID = nil
      }
    }

    Function("onRingingScreenMounted") {
      // In iOS CallKit handles lockscreen UI natively; hook provided for parity with Android
    }
  }

  private func setupCallKitProvider() {
    let appName = Bundle.main.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String
      ?? Bundle.main.object(forInfoDictionaryKey: "CFBundleName") as? String
      ?? "Call"

    let config = CXProviderConfiguration(localizedName: appName)
    config.supportsVideo = false
    config.maximumCallsPerCallGroup = 1
    config.supportedHandleTypes = [.generic]

    self.provider = CXProvider(configuration: config)
    self.provider?.setDelegate(self, queue: DispatchQueue.main)
  }

  // MARK: - CXProviderDelegate

  public func providerDidReset(_ provider: CXProvider) {
    self.activeCallUUID = nil
  }

  public func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
    var eventData: [String: Any] = [:]
    if let chatId = self.currentChatId { eventData["chatId"] = chatId }
    if let callerId = self.currentCallerId { eventData["callerId"] = callerId }

    sendEvent("onAnswer", eventData)
    action.fulfill()
  }

  public func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
    var eventData: [String: Any] = [:]
    if let chatId = self.currentChatId { eventData["chatId"] = chatId }
    if let callerId = self.currentCallerId { eventData["callerId"] = callerId }

    sendEvent("onHangUp", eventData)
    self.activeCallUUID = nil
    action.fulfill()
  }
}
