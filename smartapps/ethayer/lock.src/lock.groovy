definition (
  name: 'Lock',
  namespace: 'ethayer',
  author: 'Erik Thayer',
  description: 'App to manage locks. This is a child app.',
  category: 'Safety & Security',

  parent: 'ethayer:Lock Manager',
  iconUrl: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png',
  iconX2Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png',
  iconX3Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png'
)
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder

preferences {
  page name: 'landingPage'
  page name: 'setupPage'
  page name: 'mainPage'
  page name: 'errorPage'
  page name: 'notificationPage'
  page name: 'helloHomePage'
  page name: 'infoRefreshPage'
}

def installed() {
  debugger("Installed with settings: ${settings}")
  initialize()
}

def updated() {
  debugger("Updated with settings: ${settings}")
  initialize()
}

def initialize() {
  // reset listeners
  unsubscribe()
  unschedule()

  subscribe(lock, 'codeReport', updateCode, [filterEvents:false])
  subscribe(lock, "reportAllCodes", pollCodeReport, [filterEvents:false])
  subscribe(lock, "lock", codeUsed)
  setupLockData()
}

def isUniqueLock() {
  def unique = true
  if (!state.installComplete) {
    // only look if we're not initialized yet.
    def lockApps = parent.getLockApps()
    lockApps.each { lockApp ->
      if (lockApp.lock.id == lock.id) {
        unique = false
      }
    }
  }
  return unique
}

def landingPage() {
  if (lock) {
    def unique = isUniqueLock()
    if (unique){
      mainPage()
    } else {
      errorPage()
    }
  } else {
    setupPage()
  }
}

def setupPage() {
  dynamicPage(name: "setupPage", title: "Setup Lock", nextPage: "landingPage", uninstall: true) {
    section("Choose devices for this lock") {
      input(name: "lock", title: "Which Lock?", type: "capability.lock", multiple: false, required: true)
      input(name: "contactSensor", title: "Which contact sensor?", type: "capability.contactSensor", multiple: false, required: false)
    }
  }
}

def mainPage() {
  dynamicPage(name: "mainPage", title: "Lock Settings", install: true, uninstall: true) {
    section("Settings") {
      def actions = location.helloHome?.getPhrases()*.label
      href(name: 'toNotificationPage', page: 'notificationPage', title: 'Notification Settings')
      if (actions) {
        href(name: 'toHelloHomePage', page: 'helloHomePage', title: 'Hello Home Settings')
      }
    }
    section('Setup', hideable: true, hidden: true) {
      label title: 'Label', defaultValue: "Lock: ${lock.label}", required: false, description: 'recommended to start with Lock:'
      input(name: 'lock', title: 'Which Lock?', type: 'capability.lock', multiple: false, required: true)
      input(name: 'contactSensor', title: 'Which contact sensor?', type: "capability.contactSensor", multiple: false, required: false)
      input(name: 'slotCount', title: 'How many slots?', type: 'number', multiple: false, required: false, description: 'Overwrite number of slots supported.')
    }
  }
}

def isInit() {
  return (state.initializeComplete && state.refreshComplete)
}

def errorPage() {
  dynamicPage(name: 'errorPage', title: 'Lock Duplicate', uninstall: true, nextPage: 'landingPage') {
    section('Oops!') {
      paragraph 'The lock that you selected is already installed. Please choose a different Lock or choose Remove'
    }
    section('Choose devices for this lock') {
      input(name: 'lock', title: 'Which Lock?', type: 'capability.lock', multiple: false, required: true)
      input(name: 'contactSensor', title: 'Which contact sensor?', type: 'capability.contactSensor', multiple: false, required: false)
    }
  }
}

def notificationPage() {
  dynamicPage(name: 'notificationPage', title: 'Notification Settings') {
    section {
      paragraph 'Some options only work on select locks'
      if (!state.supportsKeypadData) {
        paragraph 'This lock only reports manual messages.\n It does not support code on lock or lock on keypad.'
      }
      if (phone == null && !notification && !recipients) {
        input(name: 'muteLock', title: 'Mute this lock?', type: 'bool', required: false, submitOnChange: true, defaultValue: false, description: 'Mute notifications for this user if notifications are set globally')
      }
      if (!muteLock) {
        input('recipients', 'contact', title: 'Send notifications to', submitOnChange: true, required: false, multiple: true)
        if (!recipients) {
          input(name: 'phone', type: 'text', title: 'Text This Number', description: 'Phone number', required: false, submitOnChange: true)
          paragraph 'For multiple SMS recipients, separate phone numbers with a semicolon(;)'
          input(name: 'notification', type: 'bool', title: 'Send A Push Notification', description: 'Notification', required: false, submitOnChange: true)
        }
        if (phone != null || notification || recipients) {
          input(name: 'notifyManualLock', title: 'On Manual Turn (Lock)', type: 'bool', required: false)
          input(name: 'notifyManualUnlock', title: 'On Manual Turn (Unlock)', type: 'bool', required: false)
          if (state.supportsKeypadData) {
            input(name: 'notifyKeypadLock', title: 'On Keypad Press to Lock', type: 'bool', required: false)
          }
        }
      }
    }
    if (!muteLock) {
      section('Only During These Times (optional)') {
        input(name: 'notificationStartTime', type: 'time', title: 'Notify Starting At This Time', description: null, required: false)
        input(name: 'notificationEndTime', type: 'time', title: 'Notify Ending At This Time', description: null, required: false)
      }
    }
  }
}

def helloHomePage() {
  dynamicPage(name: 'helloHomePage', title: 'Hello Home Settings (optional)') {
    def actions = location.helloHome?.getPhrases()*.label
    actions?.sort()
    section('Manual (Knob) Routines', hideable: true, hidden: false) {
      input(name: 'manualLockRoutineDay', title: 'On Lock Daytime', type: 'enum', options: actions, required: false, multiple: true)
      input(name: 'manualLockRoutineNight', title: 'On Lock Evening', type: 'enum', options: actions, required: false, multiple: true)
      input(name: 'manualUnlockRoutineDay', title: 'On Unlock Daytime', type: 'enum', options: actions, required: false, multiple: true)
      input(name: 'manualUnlockRoutineNight', title: 'On Unlock Evening', type: 'enum', options: actions, required: false, multiple: true)
    }
    section('Keypad Routines', hideable: true, hidden: false) {
      input(name: 'codeLockRoutineDay', title: 'On Lock Daytime', type: 'enum', options: actions, required: false, multiple: true)
      input(name: 'codeLockRoutineNight', title: 'On Lock Evening', type: 'enum', options: actions, required: false, multiple: true)
      input(name: 'codeUnlockRoutineDay', title: 'On Unlock Daytime', type: 'enum', options: actions, required: false, multiple: true)
      input(name: 'codeUnlockRoutineNight', title: 'On Unlock Evening', type: 'enum', options: actions, required: false, multiple: true)
    }
    section('Restrictions', hideable: true, hidden: true) {
      paragraph 'These restrictions apply to all the above:'
      input "userNoRunPresence", "capability.presenceSensor", title: "DO NOT run Actions if any of these are present:", multiple: true, required: false
      input "userDoRunPresence", "capability.presenceSensor", title: "ONLY run Actions if any of these are present:", multiple: true, required: false
    }
  }
}

def queSetupLockData() {
  runIn(10, setupLockData)
}

def setupLockData() {
  debugger('run lock data setup')

  def lockUsers = parent.getUserApps()
  lockUsers.each { lockUser ->
    // initialize data attributes for this lock.
    lockUser.initializeLockData()
  }
  if (state.requestCount == null) {
    state.requestCount = 0
  }

  def needPoll = initSlots()

  if (needPoll || !state.initializeComplete) {
    debugger('needs poll')
    // get report from lock -> reportAllCodes()
    lock.poll()
  }
  setCodes()
}

def makeRequest() {
  def requestSlot = false
  def codeSlots = lockCodeSlots()
  initSlots()
  (1..codeSlots).each { slot ->
    def codeState = state.codes["slot${slot}"]['codeState']
    if (codeState != 'known') {
      requestSlot = slot
    }
  }
  if (lock && requestSlot && withinAllowed()) {
    // there is an unknown code!
    state.requestCount = state.requestCount + 1
    debugger("request ${requestSlot} count: ${state.requestCount}")
    runIn(5, makeRequest)
    lock.requestCode(requestSlot)
  } else if (!withinAllowed()){
    debugger('Codes not retreived in reasonable time')
    debugger('Is the lock requestCode avalible for this lock?')
    state.refreshComplete = true

    // run a poll and reset everthing
    lock.poll()
  } else {
    debugger('no request to make')
    state.requestCount = 0
    state.refreshComplete = true
    state.initializeComplete = true
    setCodes()
  }
}

def initSlots() {
  def codeSlots = lockCodeSlots()
  def needPoll = false

  if (state.codes == null) {
    // new install!  Start learning!
    state.codes = [:]
    state.requestCount = 0
    state.initializeComplete = false
    state.installComplete = true
    state.refreshComplete = true
    state.supportsKeypadData = true
    state.pinLength = false
    if (lock.hasAttribute('pinLength')) {
      state.pinLength = lock.latestValue('pinLength')
    }
  }

  (1..codeSlots).each { slot ->
    if (state.codes["slot${slot}"] == null) {
      needPoll = true

      state.initializeComplete = false
      state.codes["slot${slot}"] = [:]
      state.codes["slot${slot}"].slot = slot
      state.codes["slot${slot}"].code = null
      state.codes["slot${slot}"].attempts = 0
      state.codes["slot${slot}"].codeState = 'unknown'
    }
  }
  return needPoll
}

def withinAllowed() {
  return (state.requestCount <= allowedAttempts())
}

def allowedAttempts() {
  return lockCodeSlots() * 2
}

def updateCode(event) {
  def data = new JsonSlurper().parseText(event.data)
  def slot = event.value.toInteger()
  def code
  if (data.code.isNumber()) {
    code = data.code
  } else {
    // It's easier on logic if code is empty to be null
    code = null
  }

  def previousCode = state.codes["slot${slot}"]['code']

  state.codes["slot${slot}"]['code'] = code
  state.codes["slot${slot}"]['codeState'] = 'known'

  debugger("Received: s:${slot} c:${code}")

  // check logic to see if all codes are in known state
  if (!state.refreshComplete) {
    runIn(5, makeRequest)
  }
  if (previousCode != code) {
    // code changed, let's inform!
    codeInform(slot, code)
  }
}

def pollCodeReport(evt) {
  def codeData = new JsonSlurper().parseText(evt.data)

  state.codeSlots = codeData.codes
  def codeSlots = lockCodeSlots()
  initSlots()

  debugger("Received: ${codeData}")
  (1..codeSlots).each { slot->
    def code = codeData."code${slot}"
    if (code != null) { //check to make sure code isn't already null, which will cause .isNumber() to error. --DiddyWolf
      if (code.isNumber()) {
        // do nothing, looks good!
        } else {
        // It's easier on logic if code is empty to be null
         code = null
      }
    }

    state.codes["slot${slot}"]['code'] = code
    if (state.codes["slot${slot}"]['codeState'] != 'refresh') {
      // don't change state if code in refresh mode
      state.codes["slot${slot}"]['codeState'] = 'known'
    }
  }
  state.initializeComplete = true
  // Set codes loaded, set new codes.
  setCodes()
}

def timeZone() {
  def zone
  if(location.timeZone) {
    zone = location.timeZone
  } else {
    zone = TimeZone.getDefault()
  }
  return zone
}

def rightNow() {
  def now = new Date().format("yyyy-MM-dd'T'HH:mm:ss.SSSZ", timeZone())
  return Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", now)
}


def codeUsed(evt) {
  def lockId = lock.id
  def message = ''
  def action = evt.value
  def userApp = false
  def codeUsed = false
  def manualUse = false
  def data = false
  def sunriseSunset = getSunriseAndSunset()
  def now = rightNow()
  def isEvening = now >= sunriseSunset.sunset && now <= (sunriseSunset.sunrise + 1)

  if (evt.data) {
    data = new JsonSlurper().parseText(evt.data)
    codeUsed = data.usedCode
    if (codeUsed.isNumber()) {
      userApp = findSlotUserApp(codeUsed)
    }
  }

  if (!data || data?.usedCode == 'manual') {
    manualUse = true
  }
  
  log.debug("data: ${data}")
  log.debug("manual: ${manualUse}, userApp: ${userApp}")

  if (action == 'unlocked') {
    // door was unlocked
    if (userApp) {
      message = "${lock.label} was unlocked by ${userApp.userName}"
      userApp.incrementLockUsage(lock.id)
      if (!userApp.isNotBurned()) {
        parent.setAccess()
        message += '.  Now burning code.'
      }
      // user specific
      if (userApp.userUnlockPhrase) {
        userApp.executeHelloPresenceCheck(userApp.userUnlockPhrase)
      }
      
      // lock specific
      if (isEvening) {
        if (codeUnlockRoutineNight) {
          executeHelloPresenceCheck(codeUnlockRoutineNight)
        }
      } else {
        if (codeUnlockRoutineDay) {
          executeHelloPresenceCheck(codeUnlockRoutineDay)
        }
      }
    } else if (manualUse) {
      // unlocked manually

      if (isEvening) {
        if (manualUnlockRoutineNight) {
          executeHelloPresenceCheck(manualUnlockRoutineNight)
        }
      } else {
        if (manualUnlockRoutineDay) {
          executeHelloPresenceCheck(manualUnlockRoutineDay)
        }
      }

      message = "${lock.label} was unlocked manually"
      if (notifyManualUnlock) {
        send(message)
      }
    }
  }
  if (action == 'locked') {
    // door was locked
    if (userApp) {
      message = "${lock.label} was locked by ${userApp.userName}"
    }
    if (data && data.usedCode == -1) {
      message = "${lock.label} was locked by keypad"
      
      if (isEvening) {
        if (codeLockRoutineNight) {
          executeHelloPresenceCheck(codeLockRoutineNight)
        }
      } else {
        if (codeLockRoutineDay) {
          executeHelloPresenceCheck(codeLockRoutineDay)
        }
      }

      if (notifyKeypadLock) {
        send(message)
      }
    }
    if (manualUse) {
      // locked manually
      message = "${lock.label} was locked manually"

      if (isEvening) {
        if (manualLockRoutineNight) {
          executeHelloPresenceCheck(manualLockRoutineNight)
        }
      } else {
        if (manualLockRoutineDay) {
          executeHelloPresenceCheck(manualLockRoutineDay)
        }
      }

      if (notifyManualLock) {
        send(message)
      }
    }
  }

  // decide if we should send a message per the userApp
  if (userApp && message) {
    debugger("Sending message: " + message)
    if (action == 'unlocked') {
      if (userApp.notifyAccess || parent.notifyAccess) {
        userApp.send(message)
      }
    } else if (action == 'locked') {
      if (userApp.notifyLock || parent.notifyLock) {
        userApp.send(message)
      }
    }
  }
}

def setCodes() {
  def codes = state.codes
  def sortedCodes = codes.sort{it.value.slot}
  sortedCodes.each { data ->
    data = data.value
    def lockUser = findSlotUserApp(data.slot)
    if (lockUser) {
      if (lockUser.isActive(lock.id)) {
        // is active, should be set
        state.codes["slot${data.slot}"].correctValue = lockUser.userCode.toString()
      } else {
        // is inactive, should not be set
        state.codes["slot${data.slot}"].correctValue = null
      }
    } else if (parent.overwriteMode) {
      state.codes["slot${data.slot}"].correctValue = null
    } else {
      // do nothing!
    }
  }
  if (state.initializeComplete && state.refreshComplete) {
    runIn(5, loadCodes)
  } else {
    debugger('initialize not yet complete!')
  }
}

def loadCodes() {
  debugger('running load codes')
  def array = []
  def codes = state.codes
  def sortedCodes = codes.sort{it.value.slot}
  sortedCodes.each { data ->
    data = data.value
    def currentCode = data.code.toString()
    def correctCode = data.correctValue.toString()
    if (currentCode != correctCode) {
      debugger("${currentCode}:${correctCode} s:${data.slot}")
      if (data.attempts <= 10) {
        def code
        if (data.correctValue) {
          code = data.correctValue
        } else {
          code = ''
        }
        array << ["code${data.slot}", code]
        state.codes["slot${data.slot}"].attempts = data.attempts + 1
      } else {
        state.codes["slot${data.slot}"].attempts = 0
        def userApp = findSlotUserApp(data.slot)
        if (userApp) {
          userApp.disableLock(lock.id)
        }
      }
    } else {
      state.codes["slot${data.slot}"].attempts = 0
    }
  }
  def json = new groovy.json.JsonBuilder(array).toString()
  if (json != '[]') {
    debugger("update: ${json}")
    lock.updateCodes(json)
    runIn(30, setCodes)
  } else {
    debugger('No codes to set')
  }
}

def findSlotUserApp(slot) {
  def foundLockUser = false
  def lockUsers = parent.getUserApps()
  lockUsers.each { lockUser ->
    def userSlot = lockUser.userSlot
    if (userSlot.toInteger() == slot.toInteger()) {
      foundLockUser = lockUser
    }
  }
  return foundLockUser
}

def codeInform(slot, code) {
  def userApp = findSlotUserApp(slot)
  if (userApp) {
    def message = ''
    def isActive = userApp.isActive(lock.id)
    def userCode = userApp.userCode
    if (isActive) {
      if (userCode == code) {
        message = "${userApp.userName} now has access to ${lock.label}"
        if (userApp.notifyAccessStart || parent.notifyAccessStart) {
          userApp.send(message)
        }
      } else {
        // user should have access but the code is wrong!
      }
    } else {
      if (!code) {
        message = "${userApp.userName} no longer has access to ${lock.label}"
        if (userApp.notifyAccessEnd || parent.notifyAccessEnd) {
          userApp.send(message)
        }
      } else {
        // there's a code set for some reason
        // it should be deleted!
      }
    }
    debugger(message)
  }
}


def doorOpenCheck() {
  def currentState = contact.contactState
  if (currentState?.value == 'open') {
    def msg = "${contact.displayName} is open.  Scheduled lock failed."
    if (sendPushMessage) {
      sendPush msg
    }
    if (phone) {
      sendSms phone, msg
    }
  } else {
    lockMessage()
    lock.lock()
  }
}

def lockMessage() {
}


def send(message) {
  if (notificationStartTime != null && notificationEndTime != null) {
    def start = timeToday(notificationStartTime)
    def stop = timeToday(notificationEndTime)
    def now = new Date()
    if (start.before(now) && stop.after(now)){
      sendMessage(message)
    }
  } else {
    sendMessage(message)
  }
}
def sendMessage(message) {
  if (!muteLock) {
    if (recipients) {
      sendNotificationToContacts(message, recipients)
    } else {
      if (notification) {
        sendPush(message)
      } else {
        sendNotificationEvent(message)
      }
      if (phone) {
        if ( phone.indexOf(';') > 1){
          def phones = phone.split(';')
          for ( def i = 0; i < phones.size(); i++) {
            sendSms(phones[i], message)
          }
        }
        else {
          sendSms(phone, message)
        }
      }
    }
  } else {
    sendNotificationEvent(message)
  }
}

def isCodeComplete() {
  return state.initializeComplete
}

def isRefreshComplete() {
  return state.refreshComplete
}

def lockCodeSlots() {
  // default to 30
  def codeSlots = 30
  if (slotCount) {
    // return the user defined value
    codeSlots = slotCount
  } else if (state?.codeSlots) {
    codeSlots = state.codeSlots.toInteger()
    debugger("Lock has ${codeSlots} code slots.")
  }
  return codeSlots
}

def codeData() {
  return state.codes
}

def slotData(slot) {
  state.codes["slot${slot}"]
}

def enableUser(slot) {
  state.codes["slot${slot}"].attempts = 0
  runIn(10, makeRequest)
}

def pinLength() {
  return state.pinLength
}

def debugger(message) {
}

def anyoneHome(sensors) {
  def result = false
  if(sensors.findAll { it?.currentPresence == "present" }) {
    result = true
  }
  result
}

def executeHelloPresenceCheck(routines) {
  log.debug("Running hello ${routines}")
  if (userNoRunPresence && userDoRunPresence == null) {
    if (!anyoneHome(userNoRunPresence)) {
      location.helloHome.execute(routines)
    }
  } else if (userDoRunPresence && userNoRunPresence == null) {
    if (anyoneHome(userDoRunPresence)) {
      location.helloHome.execute(routines)
    }
  } else if (userDoRunPresence && userNoRunPresence) {
    if (anyoneHome(userDoRunPresence) && !anyoneHome(userNoRunPresence)) {
      location.helloHome.execute(routines)
    }
  } else {
    location.helloHome.execute(routines)
  }
}
