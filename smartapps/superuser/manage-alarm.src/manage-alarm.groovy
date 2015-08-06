/**
 *  Manage Alarm
 *
 *  Author: Carlo Innocenti
 *  Date: 2014-04-02
 */

// Automatically generated. Make future change here.
definition(
    name: "Manage Alarm",
    namespace: "",
    author: "Carlo Innocenti",
    description: "Manage Alarm",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience%402x.png"
)

preferences {
	section("Settings") {
		input "alarm", "capability.Alarm", title: "Alarm device"
		input "awayMode", "mode", title: "Away mode"
	}
	section("Night management") {
		input "sleepAlarmMode", "mode", title: "Sleep with alarm mode", required: false
		input "people", "capability.presenceSensor", title: "These sensors must be present", multiple: true, required: false
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
    subscribe(location, modeChangeHandler)
	subscribe(people, "presence", presenceHandler)
    updateState(location.mode)
}

def modeChangeHandler(evt) {
	updateState(evt.value)
}

private updateState(mode) {
	log.debug "updateState(${mode})"
    unschedule(checkAlarmCommand)
	if (mode == awayMode) {
    	alarm.alarmOn()
        state.requested = "away"
        runIn(400, checkAlarmCommand)
    } else if (sleepAlarmMode && mode == sleepAlarmMode && everyoneIsPresent()) {
    	alarm.alarmStay()
        state.requested = "stay"
        runIn(400, checkAlarmCommand)
    } else {
    	alarm.alarmOff()
        state.requested = "off"
        runIn(400, checkAlarmCommand)
    }
    runIn(60, poll)
}

def checkAlarmCommand() {
	if (alarm.currentValue("alarmStatus") != state.requested) {
    	def msg = "Alarm ${alarm.displayName} switch to ${state.requested} has failed (alarm is ${alarm.currentValue("alarmStatus")})"
        log.error msg
    	sendNotification(msg)
    } else {
    	def msg = "Alarm ${alarm.displayName} successfully switched or confirmed to ${state.requested}"
        log.info msg
    }
}

def poll() {
	log.debug "poll"
	alarm.poll()
}

def presenceHandler(evt)
{
	log.debug "presenceHandler: $evt.value"
	if (evt.value == "present" && location.mode == sleepAlarmMode) {
		runIn(300, "updatePresence")
	}
}

def updatePresence() {
    updateState(location.mode)
}

private everyoneIsPresent()
{
	def result = true
	for (person in people) {
		if (person.currentPresence != "present") {
			result = false
			break
		}
	}
	log.debug "everyoneIsPresent: $result"
	return result
}
