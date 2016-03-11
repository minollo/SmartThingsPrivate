preferences {
  input("TCusername", "text", title: "Username", description: "Your TC username")
  input("TCpassword", "password", title: "Password", description: "Your TC password")
  input("TClocation", "text", title: "Location", description: "Your TC location")
}


// for the UI
metadata {
	definition (name: "TotalConnect 2.0", author: "minollo@minollo.com", namespace: "security") {
		capability "Polling"
		capability "Alarm"

		attribute "alarmStatus", "string"
		attribute "bypassStatus", "string"
		attribute "enabled", "string"

		command "alarmOn"
		command "alarmStay"
		command "alarmOff"
		command "toggleAlarm"
		command "toggleEnable"
        command "poll"
	}

  tiles {
	standardTile("alarmStatus", "device.alarmStatus", width: 2, height: 2, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false) {
      state "off", label: "off", action: "toggleAlarm", icon: "", backgroundColor: "#79b821"
      state "away", label: "away", action: "toggleAlarm", icon: "",  backgroundColor: "#F70000"
      state "stay", label: "stay", action: "toggleAlarm", icon: "",  backgroundColor: "#F7B900"
      state "arming", label: "arming", icon: "",  backgroundColor: "#C09FA6"
      state "disarming", label: "disarming", icon: "",  backgroundColor: "#A4C09F"
      state "unknown", label: "unknown", icon: "",  backgroundColor: "#FFFFFF"
    }

    standardTile("refresh", "device.alarmStatus", inactiveLabel: false, decoration: "flat") {
      state "default", action:"polling.poll", icon:"st.secondary.refresh"
    }

	standardTile("enable", "device.enabled", canChangeIcon: false, inactiveLabel: true) {
      state "default", label: "enabled", action: "toggleEnable", icon: "", backgroundColor: "#79b821"
      state "false", label: "disabled", action: "toggleEnable", icon: "",  backgroundColor: "#F70000"
   	}

	standardTile("bypass", "device.bypassStatus", canChangeIcon: false, inactiveLabel: true) {
      state "default", label: "no bypass", icon: "", backgroundColor: "#A4C09F"
      state "true", label: "bypassed", icon: "",  backgroundColor: "#C09FA6"
   	}

	main "alarmStatus"
      details(["alarmStatus", "enable", "bypass", "refresh"])
  }
}



def toggleEnable() {
	def currentStatus = device.currentValue("enabled")
	log.debug "toggleEnable() from ${currentStatus}"
    if (currentStatus == "false") {
		sendEvent(name: "enabled", value: "true")
	}  else {
		alarmOff()
		sendEvent(name: "enabled", value: "false")
	}
}

def toggleAlarm() {
	state.sessionID = null	//use as a way to force a new session
	def currentStatus = device.currentValue("alarmStatus")
	log.debug "toggleAlarm() from ${currentStatus}"
    if (currentStatus == "away" || currentStatus == "stay") {
		alarmOff()
	}  else if (currentStatus == "off") {
		alarmOn()
	}
}


def alarmOn() {
	def enabled = device.currentValue("enabled")
	log.debug "alarmOn() - enabled == ${enabled}"
	def currentStatus = device.currentValue("alarmStatus")
    if (enabled == "true") {
    	state.latestRequestedStatus = "away"
    	if (currentStatus == "off") {
            def sessionID = getSession(TCusername, TCpassword)
            if (sessionID) {
                def sessionDetails = getSessionDetails(sessionID, TClocation)
                def locationID = sessionDetails[0]
                def deviceID = sessionDetails[1]
                def userID = sessionDetails[2]
                if (locationID && deviceID) {
                    def status = getArmedStatus(sessionID, locationID)
                    if (status && status[0] && status[0] == "off") {
                        def userPIN = getUserPIN(sessionID, locationID, deviceID, userID)
                        armAlarm(sessionID, locationID, deviceID, userPIN, "away")
                    }
                }
            }
    	} else {
        	log.warning "Alarm is not off; ignoring alarmOn()"
        }
	} else {
    	log.debug "Ignoring command"
    }
}

def alarmStay() {
	def enabled = device.currentValue("enabled")
	log.debug "alarmOn() - enabled == ${enabled}"
    if (enabled == "true") {
    	state.latestRequestedStatus = "stay"
		def currentStatus = device.currentValue("alarmStatus")
      	if (currentStatus == "off") {
            def sessionID = getSession(TCusername, TCpassword)
            if (sessionID) {
                def sessionDetails = getSessionDetails(sessionID, TClocation)
                def locationID = sessionDetails[0]
                def deviceID = sessionDetails[1]
                def userID = sessionDetails[2]
                if (locationID && deviceID) {
                    def status = getArmedStatus(sessionID, locationID)
                    if (status && status[0] && status[0] == "off") {
                        def userPIN = getUserPIN(sessionID, locationID, deviceID, userID)
                        armAlarm(sessionID, locationID, deviceID, userPIN, "stay")
                    }
                }
            }
    	} else {
        	log.warning "Alarm is not off; ignoring alarmStay()"
        }
	} else {
    	log.debug "Ignoring command"
    }
}

def alarmOff() {
	log.debug "alarmOff()"
	state.latestRequestedStatus = "off"
	def sessionID = getSession(TCusername, TCpassword)
    if (sessionID) {
	    def sessionDetails = getSessionDetails(sessionID, TClocation)
    	def locationID = sessionDetails[0]
	    def deviceID = sessionDetails[1]
        def userID = sessionDetails[2]
        if (locationID && deviceID) {
       		def status = getArmedStatus(sessionID, locationID)
            if (status && status[0] && (status[0] == "away" || status[0] == "stay")) {
		        def userPIN = getUserPIN(sessionID, locationID, deviceID, userID)
                disarmAlarm(sessionID, locationID, deviceID, userPIN)
            }
        }
	}
}




def api(method, args, success) {
	def baseURL = "https://rs.alarmnet.com/tc21api/tc2.asmx"
	def methods = [
    	"getSession": [uri: "${baseURL}/AuthenticateUserLogin?${args}"],
    	"getSessionDetails": [uri: "${baseURL}/GetSessionDetails?${args}"],
    	"getPanelStatus": [uri: "${baseURL}/GetPanelMetaDataAndFullStatus?${args}"],
    	"getUserDetails": [uri: "${baseURL}/GetUserDetails?${args}"],
    	"armAlarm": [uri: "${baseURL}/ArmSecuritySystem?${args}"],
    	"disarmAlarm": [uri: "${baseURL}/DisarmSecuritySystem?${args}"],
    	"keepAlive": [uri: "${baseURL}/KeepAlive?${args}"],
    	"logOut": [uri: "${baseURL}/Logout?${args}"]
	]

	def request = methods.getAt(method)
//	log.debug "httpGet: ${request}"
	httpGet(request.uri, success)
}



private getSession(username, password) {
	def sessionID
	def nowTime = new Date().time
    if (state.sessionTime && nowTime - state.sessionTime < (3 * 60 * 1000 + 20000)) {
    	sessionID = state.sessionID
    }
	def result = {
    	response ->
        	if (response.data && response.data.name() == "AuthenticateLoginResults") {
            	def resultCode = response.data.ResultCode.text()
                log.debug "getSession().resultCode == ${resultCode}"
                if (resultCode != "0") {
                	log.error "getSession(): ${response.data.ResultData.text()}"
                } else {
                	sessionID = response.data.SessionID.text()
                    log.info "getSession(): ${sessionID}"
                }
			} else {
            	log.error "getSession(): catastrophic failure"
            }
    }
    if (keepAlive(sessionID) != "0") {
    	log.debug "Creating new session"
		api("getSession", "userName=${username}&password=${password}&ApplicationID=34857971&ApplicationVersion=2.2.0", result)
        state.sessionID = sessionID
    } else {
    	log.debug "Using existing session: ${sessionID}"
    }
	state.sessionTime = nowTime
    sessionID
}

private keepAlive(sessionID) {
	if (sessionID == null) return null
    log.debug "KeepAlive(); sessionID == ${sessionID}"
	def resultCode
	def result = {
    	response ->
        	if (response.data && response.data.name() == "WebMethodResults") {
            	resultCode = response.data.ResultCode.text()
                log.debug "keepAlive().resultCode == ${resultCode}"
			} else {
            	log.error "keepAlive(): catastrophic failure"
            }
    }
	api("keepAlive", "SessionID=${sessionID}", result)
    resultCode
}

private getSessionDetails(sessionID, location) {
	def locationID
    def deviceID
    def userID
	def result = {
    	response ->
        	if (response.data && response.data.name() == "SessionDetailResults") {
            	def resultCode = response.data.ResultCode.text()
                log.debug "getSessionDetails().resultCode == ${resultCode}"
                if (resultCode != "0") {
                	log.error "getSessionDetails(): ${response.data.ResultData.text()}"
                } else {
                	userID = response.data.UserInfo.UserID.text()
                	response.data.Locations.LocationInfoBasic.each { 
                    	log.debug "locationName = ${it.LocationName.text()} (looking for '${location}')"
                    	if (it.LocationName.text() == location) {
                        	locationID = it.LocationID.text()
                            deviceID = it.DeviceList.DeviceInfoBasic[0].DeviceID.text()
                        }
					}
                    log.info "getSessionDetails(): ${locationID}, ${deviceID}, ${userID}"
                }
			} else {
            	log.error "getSessionDetails(): catastrophic failure"
            }
    }
	api("getSessionDetails", "SessionID=${sessionID}&ApplicationID=34857971&ApplicationVersion=2.2.0", result)
    [locationID, deviceID, userID]
}

private getArmedStatus(sessionID, locationID) {
	def armedStatus
    def bypassStatus
	def result = {
    	response ->
        	if (response.data && response.data.name() == "PanelMetadataAndStatusResults") {
            	def resultCode = response.data.ResultCode.text()
                log.debug "getArmedStatus().resultCode == ${resultCode}"
                if (resultCode != "0") {
                	log.error "getArmedStatus(): ${response.data.ResultData.text()}"
                } else {
                	def armedCode = response.data.PanelMetadataAndStatus.Partitions.PartitionInfo.ArmingState.text()
                    if (armedCode == "10200") {
                    	armedStatus = "off"
                        bypassStatus = "false"
                    } else if (armedCode == "10201") {
                    	armedStatus = "away"
                        bypassStatus = "false"
                    } else if (armedCode == "10202") {
                    	armedStatus = "away"
                        bypassStatus = "true"
                    } else if (armedCode == "10203") {
                    	armedStatus = "stay"
                        bypassStatus = "false"
                    } else if (armedCode == "10204") {
                    	armedStatus = "stay"
                        bypassStatus = "false"
                    } else if (armedCode == "10211") {
                    	armedStatus = "off"
                        bypassStatus = "true"
                    } else if (armedCode == "10307") {
                    	armedStatus = "arming"
                        bypassStatus = "false"
                    } else if (armedCode == "10308") {
                    	armedStatus = "disarming"
                        bypassStatus = "false"
                    } else {
                    	armedStatus = "unknown"
                        bypassStatus = "false"
                   	}
                    log.info "getArmedStatus(): ${armedCode}, ${armedStatus}, ${bypassStatus}"
                }
			} else {
            	log.error "getArmedStatus(): catastrophic failure"
            }
    }
	api("getPanelStatus", "SessionID=${sessionID}&LocationID=${locationID}&LastSequenceNumber=0&LastUpdatedTimestampTicks=0&PartitionID=0", result)
//    log.trace "SessionID=${sessionID}&LocationID=${locationID}&LastSequenceNumber=0&LastUpdatedTimestampTicks=0&PartitionID=0"
    [armedStatus, bypassStatus]
}

private getUserPIN(sessionID, locationID, deviceID, userID) {
	def userPIN
	def result = {
    	response ->
        	if (response.data && response.data.name() == "UserDetailResults") {
            	def resultCode = response.data.ResultCode.text()
                log.debug "getUserPIN().resultCode == ${resultCode}"
                if (resultCode != "0") {
                	log.error "getUserPIN(): ${response.data.ResultData.text()}"
                } else {
                	response.data.UserDetails.LocationList.LocationUserAuthorization.each {
                    	log.debug "Location ID: ${it.LocationID}"
                    	if (it.LocationID == locationID) {
                        	it.DeviceList.DeviceUserAuthorization.each {
		                    	log.debug "Device ID: ${it.DeviceID}"
                            	if (it.DeviceID == deviceID) {
                                	it.DeviceAuthorizationAttributes.DeviceAttribute.each {
                                    	log.debug "Attribute name: ${it.Name.text()}"
                                    	if (it.Name.text() == "PanelUserCode") {
                                        	userPIN = it.Value.text()
                                        }
                                    }
                                }
                            }
                        }
                    }
                    log.info "getUserPIN().length: ${userPIN.length()}"
                }
			} else {
            	log.error "getUserPIN(): catastrophic failure"
            }
    }
	api("getUserDetails", "SessionID=${sessionID}&ManageUserId=${userID}&UserTypeId=${userID}&AdditionalInput=", result)
    userPIN
}

private armAlarm(sessionID, locationID, deviceID, userPIN, stayOrAway) {
	def resultCode
	def result = {
    	response ->
        	if (response.data && response.data.name() == "ArmSecuritySystemResults") {
            	resultCode = response.data.ResultCode.text()
                log.debug "armAlarm().resultCode == ${resultCode}"
			} else {
            	log.error "armAlarm(): catastrophic failure"
            }
    }
    if (stayOrAway == "away") {
		api("armAlarm", "SessionID=${sessionID}&LocationID=${locationID}&DeviceID=${deviceID}&ArmType=0&UserCode=${userPIN}", result)
    } else {
		api("armAlarm", "SessionID=${sessionID}&LocationID=${locationID}&DeviceID=${deviceID}&ArmType=1&UserCode=${userPIN}", result)
    }
    resultCode
}

private disarmAlarm(sessionID, locationID, deviceID, userPIN) {
	def resultCode
	def result = {
    	response ->
        	if (response.data && response.data.name() == "DisarmSecuritySystemResults") {
            	resultCode = response.data.ResultCode.text()
                log.debug "disarmAlarm().resultCode == ${resultCode}"
			} else {
            	log.error "disarmAlarm(): catastrophic failure"
            }
    }
	api("disarmAlarm", "SessionID=${sessionID}&LocationID=${locationID}&DeviceID=${deviceID}&UserCode=${userPIN}", result)
    resultCode
}

private logOut(sessionID) {
	def result = {
    	response ->
        	if (response.data && response.data.name() == "WebMethodResults") {
            	def resultCode = response.data.ResultCode.text()
                log.debug "logOut().resultCode == ${resultCode}"
                if (resultCode != "0") {
                	log.error "logOut(): ${response.data.ResultData.text()}"
                } else {
                    log.info "logOut(): success"
                }
			} else {
            	log.error "logOut(): catastrophic failure"
            }
    }
	api("logOut", "SessionID=${sessionID}", result)
}

def poll() {
	log.debug "Poll: latestRequestedStatus == ${state.latestRequestedStatus}"
    def sessionID = getSession(TCusername, TCpassword)
    if (sessionID) {
	    def sessionDetails = getSessionDetails(sessionID, TClocation)
    	def locationID = sessionDetails[0]
	    def deviceID = sessionDetails[1]
        def userID = sessionDetails[2]
        if (locationID && deviceID) {
       		def status = getArmedStatus(sessionID, locationID)
	        sendEvent(name: "alarmStatus", value: status[0])
	        sendEvent(name: "bypassStatus", value: status[1])
            log.debug "Current alarmStatus == ${status[0]}"
            if(state.latestRequestedStatus != null && state.latestRequestedStatus != status[0]) {
            	if(state.latestRequestedStatus == "away") {
                	log.warn "Requesting state change to away again..."
                	alarmOn()
                } else if (state.latestRequestedStatus == "stay") {
                	log.warn "Requesting state change to stay again..."
                	alarmStay()
                } else if (state.latestRequestedStatus == "off") {
                	log.warn "Requesting state change to off again..."
                	alarmOff()
                } else {
                	log.error "Unknown state.latestRequestedStatus value"
                }
            } else {
            	log.debug "Resetting state.latestRequestedStatus"
            	state.latestRequestedStatus = null
            }
        } else {
        	state.sessionID = null
        }
	}
}

def configure() {
	log.debug "Configuring"
	poll()
}

