/**
 *  Keep Me Cozy
 *
 *  Author: SmartThings
 */

// Automatically generated. Make future change here.
definition(
    name: "Manage general thermostat",
    namespace: "",
    author: "minollo@minollo.com",
    description: "Manage general thermostat",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience%402x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience%402x.png")

preferences {
	section("Poller device...") {
    	input "pollerDevice", "capability.battery", required: false
    }
	section("Choose thermostat... ") {
		input "thermostat", "capability.thermostat"
	}
	section("Helper heaters... "){
		input "heaters", "capability.switch", title: "Heaters", multiple: true, required: false
	}
	section("Mode #1 temperature control") {
		input "mode1", "mode", title: "Mode #1?"
		input "heatingSetpoint1", "number", title: "Heating temp (F)?", required: false
		input "coolingSetpoint1", "number", title: "Cooling temp (F)?", required: false
	}
	section("Mode #2 temperature control") {
		input "mode2", "mode", title: "Mode #2?"
		input "heatingSetpoint2", "number", title: "Heating temp (F)?", required: false
		input "coolingSetpoint2", "number", title: "Cooling temp (F)?", required: false
    }
	section("Mode #3 temperature control") {
		input "mode3", "mode", title: "Mode #3?", required: false
		input "heatingSetpoint3", "number", title: "Heating temp (F)?", required: false
		input "coolingSetpoint3", "number", title: "Cooling temp (F)?", required: false
    }
	section("Away temperature control") {
		input "heatingSetpointAway", "number", title: "Heating temp (F)?", required: false
		input "coolingSetpointAway", "number", title: "Cooling temp (F)?", required: false
    }
}

def installed()
{
	initialize()
	subscribe(thermostat, "heatingSetpoint", heatingSetpointHandler)
	subscribe(thermostat, "coolingSetpoint", coolingSetpointHandler)
	subscribe(thermostat, "thermostatOperatingState", operatingStateHandler)
	subscribe(thermostat, "temperature", temperatureHandler)
    subscribe(thermostat, "thermostatMode", thermostatModeHandler)
    if (pollerDevice) subscribe(pollerDevice, "battery", pollerEvent)
	subscribe(location, changedLocationMode)
	subscribe(app, appTouch)
    runIn(300, keepAlive)
    log.debug "Scheduling initialize"
    runIn(60, initialize)
}

def updated()
{
	unsubscribe()
    unschedule()
	subscribe(thermostat, "heatingSetpoint", heatingSetpointHandler)
	subscribe(thermostat, "coolingSetpoint", coolingSetpointHandler)
	subscribe(thermostat, "thermostatOperatingState", operatingStateHandler)
	subscribe(thermostat, "temperature", temperatureHandler)
    subscribe(thermostat, "thermostatMode", thermostatModeHandler)
    if (pollerDevice) subscribe(pollerDevice, "battery", pollerEvent)
	subscribe(location, changedLocationMode)
	subscribe(app, appTouch)
    runIn(300, keepAlive)
    log.debug "Scheduling initialize"
    runIn(60, initialize)
}


def initialize() {
	log.debug "Initializing"
	log.debug "Initial settings update"
    doUpdateTempSettings()
}

def heatingSetpointHandler(evt)
{
	log.debug "heatingSetpoint: $evt.value, $settings, temperature is ${thermostat.currentValue("temperature")}"
    handleHelperHeaters()
    if (toDouble(evt.value) >= toDouble(thermostat.currentValue("temperature")) && thermostat.currentValue("thermostatMode") != "heat" && thermostat.currentValue("thermostatMode") != "emergencyHeat") {
    	log.debug "Set mode to heat"
    	thermostat.heat()
    }
}

def coolingSetpointHandler(evt)
{
	log.debug "coolingSetpoint: $evt.value, $settings, temperature is ${thermostat.currentValue("temperature")}"
    if (toDouble(evt.value) <= toDouble(thermostat.currentValue("temperature")) && thermostat.currentValue("thermostatMode") != "cool") {
    	log.debug "Set mode to cool"
    	thermostat.cool()
    }
}

def operatingStateHandler(evt)
{
	log.debug "thermostatOperatingState: $evt.value"
	if (heaters) {
        unschedule(turnOnAuxHeaters)
    	if (evt.value != "heating" && heaters.first().currentValue("switch") == "on") {
    		log.info "Themostat not heating; shutting down any aux heat"
            heaters.off()
	    } else if (evt.value == "heating") {
    		def minutes = 30
    		log.info "Themostat started heating; turn on heaters in $minutes minutes if not done"
    		runIn(minutes * 60, turnOnAuxHeaters)	
  		}  
    }
}

def turnOnAuxHeaters() {
	log.debug "turnOnAuxHeaters; thermostatOperatingState is ${thermostat.currentValue("thermostatOperatingState")}"
	if (thermostat.currentValue("thermostatOperatingState") != "heating") {
    	log.info("Ignoring request to turn on aux heaters, as thermostat is not heating")
	} else if (location.mode.startsWith(mode1) || location.mode.startsWith(mode2) || mode3 == null || location.mode.startsWith(mode3)) {
		log.info("Thermostat has been working for a while heating; turn on AUX heaters")
    	heaters.on()
    } else {
    	log.info("Thermostat has been working for a while heating, but mode is away; ignore the problem")
    }
}

def temperatureHandler(evt)
{
	log.debug "currentTemperature: $evt.value, $settings"
    handleHelperHeaters()
    if (toDouble(evt.value) <= toDouble(thermostat.currentValue("heatingSetpoint")) && thermostat.currentValue("thermostatMode") != "heat" && thermostat.currentValue("thermostatMode") != "emergencyHeat") {
    	log.debug "Set mode to heat"
    	thermostat.heat()
    } else if (toDouble(evt.value) >= toDouble(thermostat.currentValue("coolingSetpoint")) && thermostat.currentValue("thermostatMode") != "cool") {
    	log.debug "Set mode to cool"
    	thermostat.cool()
    }
}

private handleHelperHeaters() {
	if (heaters) {
        def tempNow = toDouble(thermostat.currentValue("temperature"))
        log.debug "Temperature now is: ${tempNow}"
        def tempDistance = toDouble(thermostat.currentValue("heatingSetpoint")) - toDouble(tempNow)
        log.debug "Temperature distance for heating: ${tempDistance}"
        if ( tempDistance > 2.0 && heaters.first().currentValue("switch") == "off") {
            log.info "Switching helper heaters on"
            heaters.on()
        } else if ( tempDistance <= 0.0 && heaters.first().currentValue("switch") == "on") {
            log.info "Switching helper heaters off"
            unschedule(turnOnAuxHeaters)
            heaters.off()
        }
	}
}

def thermostatModeHandler(evt)
{
	log.debug "thermostatModeHandler: $evt.value, $settings"
}

def changedLocationMode(evt)
{
    log.debug "Now it's ${new Date(now())}"
	log.debug "changedLocationMode: $evt.value, $settings"

	runIn(30, doUpdateTempSettings)	//give the system a little time to react before checking status

	log.debug "temperature now is: ${thermostat.currentValue("temperature")}"
    log.debug "thermostat state now is: ${thermostat.currentValue("thermostatMode")}"
}

def doUpdateTempSettings()
{
	log.debug "doUpdateTempSettings"
    state.latestMode = location.mode
    log.debug "Location mode is ${location.mode}"
	if (location.mode.startsWith(mode1)) {
    	if (heatingSetpoint1 && heatingSetpoint1 != "") {
            log.debug "Setting setHeatingSetpoint(${heatingSetpoint1})"
            thermostat.setHeatingSetpoint(heatingSetpoint1)
        }
        if (coolingSetpoint1 && coolingSetpoint1 != "") {
        	log.debug "Scheduling setCoolingSetpoint(${coolingSetpoint1})"
        	state.requestedCoolingSetpoint = coolingSetpoint1
            runIn(60, setCoolingSetpoint)
        }
    } else if (location.mode.startsWith(mode2)) {
    	if (heatingSetpoint2 && heatingSetpoint2 != "") {
            log.debug "Setting setHeatingSetpoint(${heatingSetpoint2})"
            thermostat.setHeatingSetpoint(heatingSetpoint2)
        }
        if (coolingSetpoint2 && coolingSetpoint2 != "") {
        	log.debug "Scheduling setCoolingSetpoint(${coolingSetpoint2})"
        	state.requestedCoolingSetpoint = coolingSetpoint2
            runIn(60, setCoolingSetpoint)
        }
    } else if (mode3 && location.mode.startsWith(mode3)) {
    	if (heatingSetpoint3 && heatingSetpoint3 != "") {
            log.debug "Setting setHeatingSetpoint(${heatingSetpoint3})"
            thermostat.setHeatingSetpoint(heatingSetpoint3)
        }
        if (coolingSetpoint3 && coolingSetpoint3 != "") {
        	log.debug "Scheduling setCoolingSetpoint(${coolingSetpoint3})"
        	state.requestedCoolingSetpoint = coolingSetpoint3
            runIn(60, setCoolingSetpoint)
        }
    } else { //away
    	if (heatingSetpointAway && heatingSetpointAway != "") {
            log.debug "Setting setHeatingSetpoint(${heatingSetpointAway})"
            thermostat.setHeatingSetpoint(heatingSetpointAway)
        }
        if (coolingSetpointAway && coolingSetpointAway != "") {
        	log.debug "Scheduling setCoolingSetpoint(${coolingSetpointAway})"
        	state.requestedCoolingSetpoint = coolingSetpointAway
            runIn(60, setCoolingSetpoint)
        }
    }
//	thermostat.poll()
}

def pollerEvent(evt) {
	log.debug "[PollerEvent]"
    if (state.keepAliveLatest && now() - state.keepAliveLatest > 450000) {
    	log.info "Waking up timer"
    	keepAlive()
    }
}

def setCoolingSetpoint() {
	log.debug "Setting setCoolingSetpoint(${state.requestedCoolingSetpoint})"
    thermostat.setCoolingSetpoint(state.requestedCoolingSetpoint)
}

def appTouch(evt)
{
	log.debug "appTouch: $evt, $settings"
	doUpdateTempSettings()
}

def keepAlive()
{
	log.debug "Polling"
    runIn(300, keepAlive)
    state.keepAliveLatest = now()
    thermostat.poll()
    if (!state.latestMode || state.latestMode != location.mode) {
		log.debug "[Main Thermostat]: Mode has changed; update settings"
    	doUpdateTempSettings()
    } else {
    	state.latestMode = location.mode
    }
}

// catchall
def event(evt)
{
//	log.debug "value: $evt.value, event: $evt, settings: $settings, handlerName: ${evt.handlerName}"
}

private toDouble(anObj) {
	if (anObj instanceof String) {
    	Double.parseDouble(anObj)
    } else {
    	anObj
    }
}
