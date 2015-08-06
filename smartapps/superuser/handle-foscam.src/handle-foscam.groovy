preferences {
	section("Away mode") {
        input "awayMode", "mode", title: "Away mode"
    }
    
	section("Move to this preset...") {
		input "newPreset", "enum", metadata:[values:['1', '2', '3']], required: false
	}

	section("Foscam cameas to control...") {
		input "foscams", "capability.imageCapture", multiple: true
	}
}


def installed() {
	subscribe(location, changeMode)
	checkMode(location.mode)
}


def updated() {
	unsubscribe()
	subscribe(location, changeMode)
   	checkMode(location.mode)
}


def changeMode(evt) {
	log.debug "[Foscam] changeMode"
	checkMode(evt.value)
}

private checkMode(newMode) {
	log.debug "[Foscam] checkMode"

	if (newMode == awayMode) {
        if(newPreset) {
            def preset = new Integer(newPreset)
            log.info("Preset: ${preset}")
            foscams?.preset(preset)
        }
	    log.info("Alarm: on")
    	foscams?.alarmOn()
	} else {
		log.info("Alarm: off")
		foscams?.alarmOff()
	}
}

