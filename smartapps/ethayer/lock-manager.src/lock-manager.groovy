definition(
  name: 'Lock Manager',
  namespace: 'ethayer',
  author: 'Erik Thayer',
  description: 'Manage locks and users',
  category: 'Safety & Security',
  iconUrl: 'https://s3.amazonaws.com/smartapp-icons/Categories/doorsAndLocks.png',
  iconX2Url: 'https://s3.amazonaws.com/smartapp-icons/Categories/doorsAndLocks2x.png',
  iconX3Url: 'https://s3.amazonaws.com/smartapp-icons/Categories/doorsAndLocks3x.png'
)
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder

preferences {
  page name: 'mainPage', title: 'Installed', install: true, uninstall: true, submitOnChange: true
  page name: 'infoRefreshPage'
  page name: 'notificationPage'
  page name: 'lockInfoPage'
}

def mainPage() {
  dynamicPage(name: 'mainPage', install: true, uninstall: true, submitOnChange: true) {
    section('Create') {
      app(name: 'locks', appName: 'Lock', namespace: 'ethayer', title: 'New Lock', multiple: true)
      app(name: 'lockUsers', appName: 'Lock User', namespace: 'ethayer', title: 'New User', multiple: true)
    }
    section('Locks') {
      def lockApps = getLockApps()
      lockApps = lockApps.sort{ it.lock.id }
      if (lockApps) {
        def i = 0
        lockApps.each { lockApp ->
          i++
          href(name: "toLockInfoPage${i}", page: 'lockInfoPage', params: [id: lockApp.lock.id], required: false, title: lockApp.label)
        }
      }
    }
    section('Global Settings') {
      href(name: 'toNotificationPage', page: 'notificationPage', title: 'Notification Settings', description: notificationPageDescription(), state: notificationPageDescription() ? 'complete' : '')
    }
    section('Advanced', hideable: true, hidden: true) {
      input(name: 'overwriteMode', title: 'Overwrite?', type: 'bool', required: true, defaultValue: true, description: 'Overwrite mode automatically deletes codes not in the users list')
      label(title: 'Label this SmartApp', required: false, defaultValue: 'Lock Manager')
    }
  }
}

def lockInfoPage(params) {
  dynamicPage(name:"lockInfoPage", title:"Lock Info") {
    def lockApp = getLockAppByIndex(params)
    if (lockApp) {
      section("${lockApp.label}") {
        def complete = lockApp.isCodeComplete()
        if (!complete) {
          paragraph 'App is learning codes.  They will appear here when received.\n Lock may require special DTH to work properly'
          lockApp.lock.poll()
        }
        def codeData = lockApp.codeData()
        if (codeData) {
          def setCode = ''
          def usage
          def para
          def image
          def sortedCodes = codeData.sort{it.value.slot}
          sortedCodes.each { data ->
            data = data.value
            if (data.codeState != 'unknown') {
              def userApp = lockApp.findSlotUserApp(data.slot)
              para = "Slot ${data.slot}"
              if (data.code) {
                para = para + "\nCode: ${data.code}"
              }
              if (userApp) {
                para = para + userApp.getLockUserInfo(lockApp.lock)
                image = userApp.lockInfoPageImage(lockApp.lock)
              } else {
                image = 'https://s3.amazonaws.com/smartapp-icons/Categories/doorsAndLocks.png'
              }
              if (data.codeState == 'refresh') {
                para = para +'\nPending refresh...'
              }
              paragraph para, image: image
            }
          }
        }
      }

      section('Lock Settings') {
        def pinLength = lockApp.pinLength()
        def lockCodeSlots = lockApp.lockCodeSlots()
        if (pinLength) {
          paragraph "Required Length: ${pinLength}"
        }
        paragraph "Slot Count: ${lockCodeSlots}"
      }
    } else {
      section() {
        paragraph 'Error: Can\'t find lock!'
      }
    }
  }
}

def notificationPage() {
  dynamicPage(name: 'notificationPage', title: 'Global Notification Settings') {
    section {
      paragraph 'These settings will apply to all users.  Settings on individual users will override these settings'

      input('recipients', 'contact', title: 'Send notifications to', submitOnChange: true, required: false, multiple: true)
      if (!recipients) {
        input(name: 'phone', type: 'text', title: 'Text This Number', description: 'Phone number', required: false, submitOnChange: true)
        paragraph 'For multiple SMS recipients, separate phone numbers with a semicolon(;)'
        input(name: 'notification', type: 'bool', title: 'Send A Push Notification', description: 'Notification', required: false, submitOnChange: true)
      }

      if (phone != null || notification || recipients) {
        input(name: 'notifyAccess', title: 'on User Entry', type: 'bool', required: false)
        input(name: 'notifyLock', title: 'on Lock', type: 'bool', required: false)
        input(name: 'notifyAccessStart', title: 'when granting access', type: 'bool', required: false)
        input(name: 'notifyAccessEnd', title: 'when revoking access', type: 'bool', required: false)
      }
    }
    section('Only During These Times (optional)') {
      input(name: 'notificationStartTime', type: 'time', title: 'Notify Starting At This Time', description: null, required: false)
      input(name: 'notificationEndTime', type: 'time', title: 'Notify Ending At This Time', description: null, required: false)
    }
  }
}

def fancyString(listOfStrings) {
  listOfStrings.removeAll([null])
  def fancify = { list ->
    return list.collect {
      def label = it
      if (list.size() > 1 && it == list[-1]) {
        label = "and ${label}"
      }
      label
    }.join(", ")
  }

  return fancify(listOfStrings)
}

def notificationPageDescription() {
  def parts = []
  def msg = ""
  if (settings.phone) {
    parts << "SMS to ${phone}"
  }
  if (settings.recipients) {
    parts << 'Sent to Address Book'
  }
  if (settings.notification) {
    parts << 'Push Notification'
  }
  msg += fancyString(parts)
  parts = []

  if (settings.notifyAccess) {
    parts << 'on entry'
  }
  if (settings.notifyLock) {
    parts << 'on lock'
  }
  if (settings.notifyAccessStart) {
    parts << 'when granting access'
  }
  if (settings.notifyAccessEnd) {
    parts << 'when revoking access'
  }
  if (settings.notificationStartTime) {
    parts << "starting at ${settings.notificationStartTime}"
  }
  if (settings.notificationEndTime) {
    parts << "ending at ${settings.notificationEndTime}"
  }
  if (parts.size()) {
    msg += ': '
    msg += fancyString(parts)
  }
  return msg
}

def installed() {
  initialize()
}

def updated() {
  unsubscribe()
  initialize()
}

def initialize() {
  def children = getChildApps()
}

def getLockAppByIndex(params) {
  def id = ''
  // Assign params to id.  Sometimes parameters are double nested.
  if (params.id) {
    id = params.id
  } else if (params.params){
    id = params.params.id
  } else if (state.lastLock) {
    id = state.lastLock
  }
  state.lastLock = id

  def lockApp = false
  def lockApps = getLockApps()
  if (lockApps) {
    def i = 0
    lockApps.each { app ->
      if (app.lock.id == state.lastLock) {
        lockApp = app
      }
    }
  }

  return lockApp
}

def availableSlots(selectedSlot) {
  def options = []
  (1..30).each { slot->
    def children = getLockApps()
    def available = true
    children.each { child ->
      def userSlot = child.userSlot
      if (!selectedSlot) {
        selectedSlot = 0
      }
      if (!userSlot) {
        userSlot = 0
      }
      if (userSlot.toInteger() == slot && selectedSlot.toInteger() != slot) {
        available = false
      }
    }
    if (available) {
      options << ["${slot}": "Slot ${slot}"]
    }
  }
  return options
}

def findAssignedChildApp(lock, slot) {
  def childApp
  def userApps = getUserApps()
  userApps.each { child ->
    if (child.userSlot?.toInteger() == slot) {
      childApp = child
    }
  }
  return childApp
}

def getUserApps() {
  def userApps = []
  def children = getChildApps()
  children.each { child ->
    if (child.userSlot) {
      userApps.push(child)
    }
  }
  return userApps
}

def getLockApps() {
  def lockApps = []
  def children = getChildApps()
  children.each { child ->
    if (child.lock) {
      lockApps.push(child)
    }
  }
  return lockApps
}

def setAccess() {
  def lockApps = getLockApps()
  lockApps.each { lockApp ->
    lockApp.makeRequest()
  }
}

def debuggerOn() {
  // needed for child apps
  return enableDebug
}

def debugger(message) {
}
