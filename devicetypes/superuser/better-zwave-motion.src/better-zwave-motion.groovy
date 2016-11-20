metadata {
	// Automatically generated. Make future change here.
	definition (name: "Better Z-Wave motion", author: "minollo@minollo.com") {
		capability "Polling"
		capability "Battery"
		capability "Motion Sensor"
		capability "Configuration"

		command "poll"
        
		attribute "responsive", "string"
}

	simulator {
		status "active": "command: 3003, payload: FF"
		status "inactive": "command: 3003, payload: 00"
	}

	// UI tile definitions
	tiles {
		standardTile("motion", "device.motion", width: 2, height: 2) {
			state("active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#53a7c0")
			state("inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff")
		}
		standardTile("refresh", "device.thermostatMode", decoration: "flat") {
			state "default", action:"polling.poll", icon:"st.secondary.refresh"
		}
		main "motion"
		details(["motion", "refresh"])
	}
}

def parse(String description) {
	def result = []
	if (description.startsWith("Err")) {
	    result = createEvent(descriptionText:description, displayed:true)
	} else {
		updateResponsiveness()
		def cmd = zwave.parse(description, [0x20: 1, 0x30: 1, 0x31: 5, 0x32: 3, 0x80: 1, 0x84: 1, 0x71: 1, 0x9C: 1])
		if (cmd) {
			log.debug "Parse in: ${cmd.CMD}"
			result << zwaveEvent(cmd)
			def nowTime = new Date().time
			def reminderAgeInMinutes = state.lastWakeUpReminder ? (nowTime - state.lastWakeUpReminder)/60000 : 1440
			log.debug "WakeUp reminder was last sent ${reminderAgeInMinutes} minutes ago"
			if (reminderAgeInMinutes >= 1440) {	//make sure every 24 hours we remind sensor to wake up regularly
                log.debug "Sending WakeUp reminder"
                state.lastWakeUpReminder = nowTime
               	result << new physicalgraph.device.HubAction(zwave.wakeUpV1.wakeUpIntervalSet(seconds: 60*60, nodeid: zwaveHubNodeId).format())
			}
            if (cmd.CMD == "8407" ) {	// WakeUpNotification
                log.debug "WakeUpNotification in parse()"
				def batteryAgeInMinutes = state.lastBatteryQuery ? (nowTime - state.lastBatteryQuery)/60000 : 600
				log.debug "Battery status was last checked ${batteryAgeInMinutes} minutes ago"
				if (batteryAgeInMinutes >= 600) {
	                log.debug "Fetching fresh battery value"
	                result << new physicalgraph.device.HubAction(zwave.batteryV1.batteryGet().format())
                }
                result << new physicalgraph.device.HubAction("delay 1200")
                result << new physicalgraph.device.HubAction(zwave.wakeUpV1.wakeUpNoMoreInformation().format())
            }
        }
    }

	log.debug "Parse result: ${result}"
	return result
}

def sensorValueEvent(Short value) {
	log.debug "Sensor value event"
	if (value == 0) {
		createEvent([ name: "motion", value: "inactive" ])
	} else if (value == 255) {
		createEvent([ name: "motion", value: "active" ])
	} else {
		[ createEvent([ name: "motion", value: "active" ]),
			createEvent([ name: "level", value: value ]) ]
	}
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd)
{
	log.debug "basicReport"
	sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd)
{
	log.debug "basicSet"
	sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv1.SensorBinaryReport cmd)
{
	sensorValueEvent(cmd.sensorValue)
}

def zwaveEvent(physicalgraph.zwave.commands.alarmv1.AlarmReport cmd)
{
	sensorValueEvent(cmd.alarmLevel)
}

def zwaveEvent(physicalgraph.zwave.commands.sensoralarmv1.SensorAlarmReport cmd)
{
	sensorValueEvent(cmd.sensorState)
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd)
{
	def map = [ displayed: true, value: cmd.scaledSensorValue.toString() ]
	switch (cmd.sensorType) {
		case 1:
			map.name = "temperature"
			map.unit = cmd.scale == 1 ? "F" : "C"
			break;
		case 2:
			map.name = "value"
			map.unit = cmd.scale == 1 ? "%" : ""
			break;
		case 3:
			map.name = "illuminance"
			map.value = cmd.scaledSensorValue.toInteger().toString()
			map.unit = "lux"
			break;
		case 4:
			// power
			map.name = "power"
			map.unit = cmd.scale == 1 ? "Btu/h" : "W"
			break;
		case 5:
			map.name = "humidity"
			map.value = cmd.scaledSensorValue.toInteger().toString()
			map.unit = cmd.scale == 0 ? "%" : ""
			break;
		case 6:
			map.name = "velocity"
			map.unit = cmd.scale == 1 ? "mph" : "m/s"
			break;
		case 8:
		case 9:
			map.name = "pressure"
			map.unit = cmd.scale == 1 ? "inHg" : "kPa"
			break;
		case 0xE:
			map.name = "weight"
			map.unit = cmd.scale == 1 ? "lbs" : "kg"
			break;
		case 0xF:
			map.name = "voltage"
			map.unit = cmd.scale == 1 ? "mV" : "V"
			break;
		case 0x10:
			map.name = "current"
			map.unit = cmd.scale == 1 ? "mA" : "A"
			break;
		case 0x12:
			map.name = "air flow"
			map.unit = cmd.scale == 1 ? "cfm" : "m^3/h"
			break;
		case 0x1E:
			map.name = "loudness"
			map.unit = cmd.scale == 1 ? "dBA" : "dB"
			break;
	}
	createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.meterv3.MeterReport cmd) {
	def map = [ displayed: true, value: cmd.scaledMeterValue ]
	if (cmd.meterType == 1) {
		map << ([
			[ name: "energy", unit: "kWh" ],
			[ name: "energy", unit: "kVAh" ],
			[ name: "power", unit: "W" ],
			[ name: "pulse count", unit: "pulses" ],
			[ name: "voltage", unit: "V" ],
			[ name: "current", unit: "A"],
			[ name: "power factor", unit: "R/Z"],
		][cmd.scale] ?: [ name: "electric" ])
	} else if (cmd.meterType == 2) {
		map << [ name: "gas", unit: ["m^3", "ft^3", "", "pulses", ""][cmd.scale] ]
	} else if (cmd.meterType == 3) {
		map << [ name: "water", unit: ["m^3", "ft^3", "gal"][cmd.scale] ]
	} else {
		map << [ name: "meter", descriptionText: cmd.toString() ]
	}
	createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv1.WakeUpNotification cmd)
{
	log.debug "WakeUpNotification"
	[descriptionText: "${device.displayName} woke up", isStateChange:  false]
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	log.debug "Battery: ${cmd}"
    if (cmd.batteryLevel) {
        def nowTime = new Date().time
        state.lastBatteryQuery = nowTime
        def map = [ name: "battery", unit: "%", isStateChange:  true ]
        if (cmd.batteryLevel == 0xFF) {
            map.value = 1
            map.descriptionText = "${device.displayName} has a low battery"
        } else {
            map.value = cmd.batteryLevel
        }
        createEvent(map)
	} else {
    	null
	}
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	log.debug "generic command"
	def event = [ displayed: false ]
	event.linkText = device.label ?: device.name
	event.descriptionText = "$event.linkText: $cmd"
	createEvent(event)
}


def configure()
{
	log.debug "Configure"
	zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:[1]).format()  // TODO: this nodeId is the hub's node id which isn't necessarily 1
}


def poll() {
	checkResponsiveness()
}

private updateResponsiveness() {
	log.debug "updateResponsiveness"
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




