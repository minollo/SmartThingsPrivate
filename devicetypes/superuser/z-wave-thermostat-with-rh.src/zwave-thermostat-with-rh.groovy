metadata {
	// Automatically generated. Make future change here.
	definition (name: "Z-Wave thermostat with RH", author: "minollo@minollo.com") {
		capability "Actuator"
		capability "Temperature Measurement"
		capability "Relative Humidity Measurement"
		capability "Thermostat"
		capability "Configuration"
		capability "Polling"
		capability "Sensor"
		capability "Battery"
		
		attribute "thermostatFanState", "string"
		attribute "responsive", "string"

		command "switchMode"
		command "switchFanMode"
        command "quickSetCool"
        command "quickSetHeat"
		command "poll"

		fingerprint deviceId: "0x08"
		fingerprint inClusters: "0x43,0x40,0x44,0x31"
	}

	// simulator metadata
	simulator {
		status "off"			: "command: 4003, payload: 00"
		status "heat"			: "command: 4003, payload: 01"
		status "cool"			: "command: 4003, payload: 02"
		status "auto"			: "command: 4003, payload: 03"
		status "emergencyHeat"	: "command: 4003, payload: 04"

		status "fanAuto"		: "command: 4403, payload: 00"
		status "fanOn"			: "command: 4403, payload: 01"
		status "fanCirculate"	: "command: 4403, payload: 06"

		status "heat 60"        : "command: 4303, payload: 01 01 3C"
		status "heat 68"        : "command: 4303, payload: 01 01 44"
		status "heat 72"        : "command: 4303, payload: 01 01 48"

		status "cool 72"        : "command: 4303, payload: 02 01 48"
		status "cool 76"        : "command: 4303, payload: 02 01 4C"
		status "cool 80"        : "command: 4303, payload: 02 01 50"

		status "temp 58"        : "command: 3105, payload: 01 22 02 44"
		status "temp 62"        : "command: 3105, payload: 01 22 02 6C"
		status "temp 70"        : "command: 3105, payload: 01 22 02 BC"
		status "temp 74"        : "command: 3105, payload: 01 22 02 E4"
		status "temp 78"        : "command: 3105, payload: 01 22 03 0C"
		status "temp 82"        : "command: 3105, payload: 01 22 03 34"

		status "idle"			: "command: 4203, payload: 00"
		status "heating"		: "command: 4203, payload: 01"
		status "cooling"		: "command: 4203, payload: 02"
		status "fan only"		: "command: 4203, payload: 03"
		status "pending heat"	: "command: 4203, payload: 04"
		status "pending cool"	: "command: 4203, payload: 05"
		status "vent economizer": "command: 4203, payload: 06"

		// reply messages
		reply "2502": "command: 2503, payload: FF"
	}

	tiles {
		valueTile("temperature", "device.temperature", width: 2, height: 2) {
			state("temperature", label:'${currentValue}°', unit:"F",
				backgroundColors:[
					[value: 31, color: "#153591"],
					[value: 44, color: "#1e9cbb"],
					[value: 59, color: "#90d2a7"],
					[value: 74, color: "#44b621"],
					[value: 84, color: "#f1d801"],
					[value: 95, color: "#d04e00"],
					[value: 96, color: "#bc2323"]
				]
			)
		}
		standardTile("mode", "device.thermostatMode", inactiveLabel: false, decoration: "flat") {
			state "off", label:'${name}', action:"switchMode", nextState:"to_heat"
			state "heat", label:'${name}', action:"switchMode", nextState:"to_cool"
			state "cool", label:'${name}', action:"switchMode", nextState:"..."
			state "auto", label:'${name}', action:"switchMode", nextState:"..."
			state "emergencyHeat", label:'${name}', action:"switchMode", nextState:"..."
			state "to_heat", label: "heat", action:"switchMode", nextState:"to_cool"
			state "to_cool", label: "cool", action:"switchMode", nextState:"..."
			state "...", label: "...", action:"off", nextState:"off"
		}
		standardTile("fanMode", "device.thermostatFanMode", inactiveLabel: false, decoration: "flat") {
			state "fanAuto", label:'${name}', action:"switchFanMode"
			state "fanOn", label:'${name}', action:"switchFanMode"
			state "fanCirculate", label:'${name}', action:"switchFanMode"
		}
		controlTile("heatSliderControl", "device.heatingSetpoint", "slider", height: 1, width: 2, inactiveLabel: false) {
			state "setHeatingSetpoint", action:"thermostat.setHeatingSetpoint", backgroundColor:"#d04e00"
		}
		valueTile("heatingSetpoint", "device.heatingSetpoint", inactiveLabel: false, decoration: "flat") {
			state "heat", label:'${currentValue}° heat', unit:"F", backgroundColor:"#ffffff"
		}
		controlTile("coolSliderControl", "device.coolingSetpoint", "slider", height: 1, width: 2, inactiveLabel: false) {
			state "setCoolingSetpoint", action:"thermostat.setCoolingSetpoint", backgroundColor: "#1e9cbb"
		}
		valueTile("coolingSetpoint", "device.coolingSetpoint", inactiveLabel: false, decoration: "flat") {
			state "cool", label:'${currentValue}° cool', unit:"F", backgroundColor:"#ffffff"
		}
		standardTile("refresh", "device.thermostatMode", inactiveLabel: false, decoration: "flat") {
			state "default", action:"polling.poll", icon:"st.secondary.refresh"
		}
		standardTile("configure", "device.configure", inactiveLabel: false, decoration: "flat") {
			state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
		}
		main "temperature"
		details(["temperature", "mode", "fanMode", "heatSliderControl", "heatingSetpoint", "coolSliderControl", "coolingSetpoint", "refresh", "configure"])
	}
}

def parse(String description)
{
	def map = createEvent(zwaveEvent(zwave.parse(description, [0x42:1, 0x43:2, 0x31:3, 0x80:1])))
	if (!map) {
		return null
	}

	updateResponsiveness()
	def result = [map]
	if (map.isStateChange && map.name in ["heatingSetpoint","coolingSetpoint","thermostatMode"]) {
		def map2 = [
			name: "thermostatSetpoint",
			unit: getTemperatureScale()
		]
		if (map.name == "thermostatMode") {
			state.lastTriedMode = map.value
			if (map.value == "cool") {
				map2.value = device.latestValue("coolingSetpoint")
				log.info "THERMOSTAT, latest cooling setpoint = ${map2.value}"
			}
			else {
				map2.value = device.latestValue("heatingSetpoint")
				log.info "THERMOSTAT, latest heating setpoint = ${map2.value}"
			}
		}
		else {
			def mode = device.latestValue("thermostatMode")
			log.info "THERMOSTAT, latest mode = ${mode}"
			if ((map.name == "heatingSetpoint" && mode == "heat") || (map.name == "coolingSetpoint" && mode == "cool")) {
				map2.value = map.value
				map2.unit = map.unit
			}
		}
		if (map2.value != null) {
			log.debug "THERMOSTAT, adding setpoint event: $map"
			result << createEvent(map2)
		}
	} else if (map.name == "thermostatFanMode" && map.isStateChange) {
		state.lastTriedFanMode = map.value
	}
	log.debug "ParseEx returned $result"
	result
}

// Event Generation

// MultiChannelCmdEncap and MultiInstanceCmdEncap are ways that devices can indicate that a message
// is coming from one of multiple subdevices or "endpoints" that would otherwise be indistinguishable
def zwaveEvent(physicalgraph.zwave.commands.multichannelv3.MultiInstanceCmdEncap cmd) {
    def encapsulatedCommand = cmd.encapsulatedCommand([0x30: 3, 0x31: 3]) // can specify command class versions here like in zwave.parse
    if (encapsulatedCommand) {
        return zwaveEvent(encapsulatedCommand)
    }
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	if (cmd.batteryLevel <= 100) {
        def nowTime = new Date().time
        state.lastBatteryGet = nowTime
        def map = [ name: "battery", unit: "%" ]
        if (cmd.batteryLevel == 0xFF || cmd.batteryLevel == 0) {
            map.value = 1
            map.descriptionText = "$device.displayName battery is low!"
        } else {
            map.value = cmd.batteryLevel
        }
        map
	} else {
    	log.warn "Bad battery value returned: ${cmd.batteryLevel}"
    	[:]
    }
}

def zwaveEvent(physicalgraph.zwave.commands.thermostatsetpointv2.ThermostatSetpointReport cmd)
{
	def map = [:]
	map.value = cmd.scaledValue.toString()
	map.unit = cmd.scale == 1 ? "F" : "C"
	map.displayed = false
	switch (cmd.setpointType) {
		case 1:
			map.name = "heatingSetpoint"
			break;
		case 2:
			map.name = "coolingSetpoint"
			break;
		default:
			return [:]
	}
	// So we can respond with same format
	state.size = cmd.size
	state.scale = cmd.scale
	state.precision = cmd.precision
	map
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv3.SensorMultilevelReport cmd)
{
	//	log.debug "SensorMultilevelReport ${cmd}"
	def map = [:]
	switch (cmd.sensorType) {
		case 1:
			// temperature
			map.value = cmd.scaledSensorValue.toString()
			map.unit = cmd.scale == 1 ? "F" : "C"
			map.name = "temperature"
			break;
		case 5:
			// humidity
			map.value = cmd.scaledSensorValue.toInteger().toString()
			map.unit = "%"
			map.name = "humidity"
			break;
	}
    map
}

def zwaveEvent(physicalgraph.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport cmd)
{
	def map = [:]
	switch (cmd.operatingState) {
		case physicalgraph.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_IDLE:
			map.value = "idle"
			break
		case physicalgraph.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_HEATING:
			map.value = "heating"
			break
		case physicalgraph.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_COOLING:
			map.value = "cooling"
			break
		case physicalgraph.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_FAN_ONLY:
			map.value = "fan only"
			break
		case physicalgraph.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_PENDING_HEAT:
			map.value = "pending heat"
			break
		case physicalgraph.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_PENDING_COOL:
			map.value = "pending cool"
			break
		case physicalgraph.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_VENT_ECONOMIZER:
			map.value = "vent economizer"
			break
	}
	map.name = "thermostatOperatingState"
	map
}

def zwaveEvent(physicalgraph.zwave.commands.thermostatfanstatev1.ThermostatFanStateReport cmd) {
	def map = [name: "thermostatFanState", unit: ""]
	switch (cmd.fanOperatingState) {
		case 0:
			map.value = "idle"
			break
		case 1:
			map.value = "running"
			break
		case 2:
			map.value = "running high"
			break
	}
	map
}

def zwaveEvent(physicalgraph.zwave.commands.thermostatmodev2.ThermostatModeReport cmd) {
	def nowTime = new Date().time
	state.lastMoreInfoGet = nowTime
	def map = [:]
	switch (cmd.mode) {
		case physicalgraph.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_OFF:
			map.value = "off"
			break
		case physicalgraph.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_HEAT:
			map.value = "heat"
			break
		case physicalgraph.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_AUXILIARY_HEAT:
			map.value = "emergencyHeat"
			break
		case physicalgraph.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_COOL:
			map.value = "cool"
			break
		case physicalgraph.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_AUTO:
			map.value = "auto"
			break
	}
	map.name = "thermostatMode"
	map
}

def zwaveEvent(physicalgraph.zwave.commands.thermostatfanmodev3.ThermostatFanModeReport cmd) {
	def map = [:]
	switch (cmd.fanMode) {
		case physicalgraph.zwave.commands.thermostatfanmodev3.ThermostatFanModeReport.FAN_MODE_AUTO_LOW:
			map.value = "fanAuto"
			break
		case physicalgraph.zwave.commands.thermostatfanmodev3.ThermostatFanModeReport.FAN_MODE_LOW:
			map.value = "fanOn"
			break
		case physicalgraph.zwave.commands.thermostatfanmodev3.ThermostatFanModeReport.FAN_MODE_CIRCULATION:
			map.value = "fanCirculate"
			break
	}
	map.name = "thermostatFanMode"
	map.displayed = false
	map
}

def zwaveEvent(physicalgraph.zwave.commands.thermostatmodev2.ThermostatModeSupportedReport cmd) {
	def supportedModes = ""
	if(cmd.off) { supportedModes += "off " }
	if(cmd.heat) { supportedModes += "heat " }
	if(cmd.auxiliaryemergencyHeat) { supportedModes += "emergencyHeat " }
	if(cmd.cool) { supportedModes += "cool " }
	if(cmd.auto) { supportedModes += "auto " }

	updateState("supportedModes", supportedModes)
}

def zwaveEvent(physicalgraph.zwave.commands.thermostatfanmodev3.ThermostatFanModeSupportedReport cmd) {
	def supportedFanModes = ""
	if(cmd.auto) { supportedFanModes += "fanAuto " }
	if(cmd.low) { supportedFanModes += "fanOn " }
	if(cmd.circulation) { supportedFanModes += "fanCirculate " }

	updateState("supportedFanModes", supportedFanModes)
}

def updateState(String name, String value) {
	state[name] = value
	device.updateDataValue(name, value)
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
	log.debug "Zwave event received: $cmd"
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	log.warn "Unexpected zwave command $cmd"
}

// Command Implementations
def poll() {
	checkResponsiveness()
	delayBetween([
		zwave.sensorMultilevelV3.sensorMultilevelGet().format(), // current temperature
    	zwave.multiChannelV3.multiInstanceCmdEncap(instance: 2).encapsulate(zwave.sensorMultilevelV3.sensorMultilevelGet()).format(),	//current RH
		zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: 1).format(),
		zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: 2).format(),
		zwave.thermostatOperatingStateV1.thermostatOperatingStateGet().format(),
        getMoreInfo(),
		getBattery(),
        setClock()
	], getStandardDelay())
}

private getMoreInfo() {	//once every 1 hour
	def nowTime = new Date().time
	def ageInMinutes = state.lastMoreInfoGet ? (nowTime - state.lastMoreInfoGet)/60000 : 60
    def batteryValue = device.currentValue("battery")
    log.debug "More info report age: ${ageInMinutes} minutes, battery charge is ${batteryValue}%"
    if (batteryValue == 100 || ageInMinutes >= 60) {
        log.debug "Fetching fresh more info values"
		[zwave.thermostatModeV2.thermostatModeGet().format(),
         "delay ${getStandardDelay()}",
		 zwave.thermostatFanModeV3.thermostatFanModeGet().format()
        ]
    } else "delay 87"
}

private getBattery() {	//once every 10 hours
	def nowTime = new Date().time
	def ageInMinutes = state.lastBatteryGet ? (nowTime - state.lastBatteryGet)/60000 : 600
    log.debug "Battery report age: ${ageInMinutes} minutes"
    if (ageInMinutes >= 600) {
        log.debug "Fetching fresh battery value"
		zwave.batteryV1.batteryGet().format()
    } else "delay 87"
}

private setClock() {	//once a day

	def nowTime = new Date().time
	def ageInMinutes = state.lastClockSet ? (nowTime - state.lastClockSet)/60000 : 1440
    log.debug "Clock set age: ${ageInMinutes} minutes"
    if (ageInMinutes >= 1440) {
        log.debug "Setting clock"
		state.lastClockSet = nowTime
        def nowCal = Calendar.getInstance(location.timeZone);
		zwave.clockV1.clockSet(hour: nowCal.get(Calendar.HOUR_OF_DAY), minute: nowCal.get(Calendar.MINUTE), weekday: nowCal.get(Calendar.DAY_OF_WEEK)).format()
    } else "delay 87"
}

def setHeatingSetpoint(degreesF) {
	setHeatingSetpoint(degreesF.toDouble())
}

def setHeatingSetpoint(Double degreesF) {
	log.debug "setHeatingSetpoint(${degreesF})"
	def p = (state.precision == null) ? 1 : state.precision
	delayBetween([
		zwave.thermostatSetpointV1.thermostatSetpointSet(setpointType: 1, scale: 1, precision: p, scaledValue: degreesF).format(),
		zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: 1).format(),
        "delay 2400"
	])
}

def setCoolingSetpoint(degreesF) {
	setCoolingSetpoint(degreesF.toDouble())
}

def setCoolingSetpoint(Double degreesF) {
	log.debug "setCoolingSetpoint(${degreesF})"
	def p = (state.precision == null) ? 1 : state.precision
	delayBetween([
    	"delay 9600",
		zwave.thermostatSetpointV1.thermostatSetpointSet(setpointType: 2, scale: 1, precision: p,  scaledValue: degreesF).format(),
		zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: 2).format(),
        "delay 2400"
	])
}

def configure() {
	delayBetween([
		zwave.thermostatModeV2.thermostatModeSupportedGet().format(),
		zwave.thermostatFanModeV3.thermostatFanModeSupportedGet().format(),
		zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:[zwaveHubNodeId]).format()
	], 2300)
}

def modes() {
	["off", "heat", "cool", "auto", "emergencyHeat"]
}

def switchMode() {
	def currentMode = device.currentState("thermostatMode")?.value
	def lastTriedMode = state.lastTriedMode ?: currentMode ?: "off"
	def supportedModes = getDataByName("supportedModes")
	def modeOrder = modes()
	def next = { modeOrder[modeOrder.indexOf(it) + 1] ?: modeOrder[0] }
	def nextMode = next(lastTriedMode)
	if (supportedModes?.contains(currentMode)) {
		while (!supportedModes.contains(nextMode) && nextMode != "off") {
			nextMode = next(nextMode)
		}
	}
	state.lastTriedMode = nextMode
	delayBetween([
		zwave.thermostatModeV2.thermostatModeSet(mode: modeMap[nextMode]).format(),
		zwave.thermostatModeV2.thermostatModeGet().format()
	], 1000)
}

def switchToMode(nextMode) {
	def supportedModes = getDataByName("supportedModes")
	if(supportedModes && !supportedModes.contains(nextMode)) log.warn "thermostat mode '$nextMode' is not supported"
	if (nextMode in modes()) {
		state.lastTriedMode = nextMode
		"$nextMode"()
	} else {
		log.debug("no mode method '$nextMode'")
	}
}

def switchFanMode() {
	def currentMode = device.currentState("thermostatFanMode")?.value
	def lastTriedMode = state.lastTriedFanMode ?: currentMode ?: "off"
	def supportedModes = getDataByName("supportedFanModes") ?: "fanAuto fanOn"
	def modeOrder = ["fanAuto", "fanCirculate", "fanOn"]
	def next = { modeOrder[modeOrder.indexOf(it) + 1] ?: modeOrder[0] }
	def nextMode = next(lastTriedMode)
	while (!supportedModes?.contains(nextMode) && nextMode != "fanAuto") {
		nextMode = next(nextMode)
	}
	switchToFanMode(nextMode)
}

def switchToFanMode(nextMode) {
	def supportedFanModes = getDataByName("supportedFanModes")
	if(supportedFanModes && !supportedFanModes.contains(nextMode)) log.warn "thermostat mode '$nextMode' is not supported"

	def returnCommand
	if (nextMode == "fanAuto") {
		returnCommand = fanAuto()
	} else if (nextMode == "fanOn") {
		returnCommand = fanOn()
	} else if (nextMode == "fanCirculate") {
		returnCommand = fanCirculate()
	} else {
		log.debug("no fan mode '$nextMode'")
	}
	if(returnCommand) state.lastTriedFanMode = nextMode
	returnCommand
}

def getDataByName(String name) {
	state[name] ?: device.getDataValue(name)
}

def getModeMap() { [
	"off": 0,
	"heat": 1,
	"cool": 2,
	"auto": 3,
	"emergencyHeat": 4
]}

def setThermostatMode(String value) {
	delayBetween([
		zwave.thermostatModeV2.thermostatModeSet(mode: modeMap[value]).format(),
		zwave.thermostatModeV2.thermostatModeGet().format()
	], standardDelay)
}

def getFanModeMap() { [
	"auto": 0,
	"on": 1,
	"circulate": 6
]}

def setThermostatFanMode(String value) {
	delayBetween([
		zwave.thermostatFanModeV3.thermostatFanModeSet(fanMode: fanModeMap[value]).format(),
		zwave.thermostatFanModeV3.thermostatFanModeGet().format()
	], standardDelay)
}

def off() {
	delayBetween([
		zwave.thermostatModeV2.thermostatModeSet(mode: 0).format(),
		zwave.thermostatModeV2.thermostatModeGet().format(),
        "delay 2400"
	])
}

def heat() {
	delayBetween([
		zwave.thermostatModeV2.thermostatModeSet(mode: 1).format(),
		zwave.thermostatModeV2.thermostatModeGet().format(),
        "delay 2400"
	])
}

def emergencyHeat() {
	delayBetween([
		zwave.thermostatModeV2.thermostatModeSet(mode: 4).format(),
		zwave.thermostatModeV2.thermostatModeGet().format(),
        "delay 2400"
	])
}

def cool() {
	delayBetween([
		zwave.thermostatModeV2.thermostatModeSet(mode: 2).format(),
		zwave.thermostatModeV2.thermostatModeGet().format(),
        "delay 2400"
	])
}

def auto() {
	delayBetween([
		zwave.thermostatModeV2.thermostatModeSet(mode: 3).format(),
		zwave.thermostatModeV2.thermostatModeGet().format(),
        "delay 2400"
	])
}

def fanOn() {
	delayBetween([
		zwave.thermostatFanModeV3.thermostatFanModeSet(fanMode: 1).format(),
		zwave.thermostatFanModeV3.thermostatFanModeGet().format(),
        "delay 2400"
	])
}

def fanAuto() {
	delayBetween([
		zwave.thermostatFanModeV3.thermostatFanModeSet(fanMode: 0).format(),
		zwave.thermostatFanModeV3.thermostatFanModeGet().format(),
        "delay 2400"
	])
}

def fanCirculate() {
	delayBetween([
		zwave.thermostatFanModeV3.thermostatFanModeSet(fanMode: 6).format(),
		zwave.thermostatFanModeV3.thermostatFanModeGet().format(),
        "delay 2400"
	])
}

private updateResponsiveness() {
    def nowTime = new Date().time
    state.lastResponse = nowTime
    if (device.currentValue("responsive") == null || device.currentValue("responsive") == "false") {
		log.info "Updating responsive attribute to true"
		sendEvent(name: "responsive", value: "true")
    }
    null
}

private checkResponsiveness() {
    def nowTime = new Date().time
    if (state.lastResponse) {
    	def lastResponseAge = (nowTime - state.lastResponse) / 60000
		log.debug "Last response from device was received ${lastResponseAge} minutes ago"
	    if (lastResponseAge >= 1440) {	//if no response was received in the last 24 hourse, set responsive to false
            if (device.currentValue("responsive") == "true") {
				log.info "Updating responsive attribute to false"
				sendEvent(name: "responsive", value: "false")
            }
		}
    }
    null
}

private getStandardDelay() {
	1800
}