/**
 *  IKEA Trådfri Puck v1.1
 *  
 *  Copyright 2019 Joel Wetzel
 *  Base on code from Roysteroonie at:  https://github.com/Roysteroonie/Hubitat/blob/master/Ikea/Ikea%20Tradfri%20Dimmer%20(Puck).groovy
 *  Which was based on code from SmartThings public DTH.
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 *
 */

import com.hubitat.zigbee.DataType

metadata {
	definition (name: "IKEA Trådfri Puck", namespace: "joelwetzel", author: "Joel Wetzel") {
		capability "Actuator"
		capability "Battery"
		capability "Configuration"
		capability "Switch"
		capability "Switch Level"

        command "ClearStates"				// Clear all device states
        
		fingerprint manufacturer: "IKEA of Sweden", model: "TRADFRI wireless dimmer", deviceJoinName: "IKEA TRÅDFRI Wireless dimmer" // 01 [0104 or C05E] 0810 02 06 0000 0001 0003 0009 0B05 1000 06 0003 0004 0006 0008 0019 1000
	}

    
preferences {
        input name: "allowDimming", type: "bool", title: "Allow dimming", defaultValue: true
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
	}
}


def logsOff(){
    log.warn "debug logging disabled..."
    
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}


def updated(){
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    
    if (logEnable) {
        runIn(1800,logsOff)
    }
    
    state.fadeState = "stopped"
    
	response(configure())
}


def getDOUBLE_STEP() { 10 }
def getSTEP() { 5 }
def getINTERVAL() { 150 }

def getONOFF_ON_COMMAND() { 0x0001 }
def getONOFF_OFF_COMMAND() { 0x0000 }
def getLEVEL_MOVE_LEVEL_COMMAND() { 0x0000 }
def getLEVEL_MOVE_COMMAND() { 0x0001 }
def getLEVEL_STEP_COMMAND() { 0x0002 }
def getLEVEL_STOP_COMMAND() { 0x0003 }
def getLEVEL_MOVE_LEVEL_ONOFF_COMMAND() { 0x0004 }
def getLEVEL_MOVE_ONOFF_COMMAND() { 0x0005 }
def getLEVEL_STEP_ONOFF_COMMAND() { 0x0006 }
def getLEVEL_STOP_ONOFF_COMMAND() { 0x0007 }
def getLEVEL_DIRECTION_UP() { "00" }
def getLEVEL_DIRECTION_DOWN() { "01" }

def getBATTERY_VOLTAGE_ATTR() { 0x0020 }
def getBATTERY_PERCENT_ATTR() { 0x0021 }

def getMFR_SPECIFIC_CLUSTER() { 0xFC10 }

def getUINT8_STR() { "20" }


def iterate() {
    if (!allowDimming) {
        return
    }
    
    def currentState = state.fadeState
    def currentLevel = device.currentValue("level")
    def currentSwitch = device.currentValue("switch")

    def newState = currentState
    def newLevel = currentLevel

    if (logEnable) {
        log.debug "iterate: ${currentState} ${currentLevel}"
    }

    if (currentState == "fadeUp") {
        newLevel = currentLevel + DOUBLE_STEP
        
        if (newLevel > 100) {
            newLevel = 100
            newState = "stopped"
            state.fadeState = newState
        }
    }
    else if (currentState == "fadeDown") {
        newLevel = currentLevel - DOUBLE_STEP
        
        if (newLevel < STEP) {
            newLevel = STEP
            newState = "stopped"
            state.fadeState = newState
        }
    }
    
    if (newLevel != currentLevel) {
        device.setLevel(newLevel)
        
        if (txtEnable) {
            log.debug "Setting level to ${newLevel}"
        }
    }
    
    if (newState != "stopped") {
        unschedule()
        runInMillis(INTERVAL, iterate)
    }
}

    
def parse(String description) {
	def results = []
	def event = zigbee.getEvent(description)

	if (event) {
        // This is things like battery reporting.
        
        if (txtEnable) {
            log.warn "EVENT ${event}"
        }
        
		results << createEvent(event)
	} 
    else {
		def descMap = zigbee.parseDescriptionAsMap(description)
        
		if (descMap.clusterInt == 0x0001) { // power configuration
			results = handleBatteryEvents(descMap)
		} 
        else if (descMap.clusterInt == 0x0000) { // on/off
			results = handleSwitchEvent(descMap)
		} 
        else if (descMap.clusterInt == 0x0008) { //level control
			results = handleIkeaDimmerLevelEvent(descMap)
		} 
        else {
            if (txtEnable) {
                log.warn "DID NOT PARSE MESSAGE for description : $description"
            }
            if (logEnable) {
                log.debug "${descMap}"
            }
		}
	}

	return results
}


def handleIkeaDimmerLevelEvent(descMap) {
	def results = []
    
    int cmd = Integer.parseInt(descMap.command)
    
	if (cmd == LEVEL_STEP_COMMAND) {
        //log.debug "LEVEL_STEP_COMMAND"
        
		results = handleStepEvent(descMap.data[0], descMap)
	} 
    else if (cmd == LEVEL_MOVE_COMMAND) {
        //log.debug "LEVEL_MOVE_COMMAND"
        
		// Treat Level Move and Level Move with On/Off as Level Step
		results = handleStepEvent(descMap.data[0], descMap)
	}
    else if (cmd == LEVEL_MOVE_ONOFF_COMMAND) {
        //log.debug "LEVEL_MOVE_ONOFF_COMMAND"
        
        // Treat Level Move and Level Move with On/Off as Level Step
		results = handleStepEvent(descMap.data[0], descMap)
    }
    else if (cmd == LEVEL_STOP_COMMAND) {
		//log.debug "LEVEL_STOP_COMMAND"
        state.fadeState = "stopped"
        unschedule()
        runInMillis(INTERVAL, iterate)
        return
	}
    else if (cmd == LEVEL_STOP_ONOFF_COMMAND) {
		//log.debug "LEVEL_STOP_ONOFF_COMMAND"
        state.fadeState = "stopped"
        unschedule()
        runInMillis(INTERVAL, iterate)
        return
	} 
    else if (cmd == LEVEL_MOVE_LEVEL_ONOFF_COMMAND) {
        //log.debug "LEVEL_MOVE_LEVEL_ONOFF_COMMAND"
        
		// The spec defines this as "Move to level with on/off". The IKEA Dimmer sends us 0x00 or 0xFF only, so we will treat this more as a
		// on/off command for the dimmer.
		if (descMap.data[0] == "00") {
            if (txtEnable) {
			    log.info "Switch Off"
            }
			results << createEvent(name: "switch", value: "off", isStateChange: true)
            state.fadeState = "stopped"
            unschedule()
		} else if (descMap.data[0] == "FF") {
			// The IKEA Dimmer sends 0xFF -- this is technically not to spec, but we will treat this as an "on"
            if (txtEnable) {
                log.info "Switch On"
            }
			results << createEvent(name: "switch", value: "on", isStateChange: true)
            if (device.currentValue("level") == 0) {
			    results << createEvent(name: "level", value: DOUBLE_STEP)
		    }
            state.fadeState = "stopped"
            unschedule()
		} else {
            log.warn "UNKNOWN LEVEL_MOVE_LEVEL_ONOFF_COMMAND"
		}
	}

	return results
}


def handleSwitchEvent(descMap) {
	def results = []
    int cmd = Integer.parseInt(descMap.command)
    
	if (cmd == ONOFF_ON_COMMAND) {
		if (device.currentValue("level") == 0) {
			results << createEvent(name: "level", value: DOUBLE_STEP)
		}
		results << createEvent(name: "switch", value: "on")
	} 
    else if (cmd == ONOFF_OFF_COMMAND) {
		results << createEvent(name: "switch", value: "off")
	}

	return results
}


def handleStepEvent(direction, descMap) {
    def results = []
    
    unschedule()

	if (direction == LEVEL_DIRECTION_UP) {
        if (device.currentValue("switch") == "on") {
            state.fadeState = "fadeUp"
            runInMillis(INTERVAL, iterate)
        }
        else {
            results << createEvent(name: "switch", value: "on")
            state.fadeState = "fadeUp"
            runInMillis(INTERVAL, iterate)
        }
    }
    else if (direction == LEVEL_DIRECTION_DOWN) {
        // This check is to prevent turning the lights on if you fade down while
        // the lights are off.
        if (device.currentValue("switch") == "on") {
            state.fadeState = "fadeDown"
            runInMillis(INTERVAL, iterate)
        }
	}

	return results
}


def handleBatteryEvents(descMap) {
	def results = []

	if (descMap.value) {
		def rawValue = zigbee.convertHexToInt(descMap.value)
        
        if (logEnable) {
            log.debug "rawValue = ${rawValue}"
        }
        
		def batteryValue = null

		if (rawValue == 0xFF) {
			// Log invalid readings to info for analytics and skip sending an event.
			// This would be a good thing to watch for and form some sort of device health alert if too many come in.
			log.error "Invalid battery reading returned"
		} else if (descMap.attrInt == BATTERY_PERCENT_ATTR) {
			batteryValue = Math.round(rawValue / 2)
		}

		if (batteryValue != null) {
			batteryValue = Math.min(100, Math.max(0, batteryValue))

			results << createEvent(name: "battery", value: batteryValue, unit: "%", descriptionText: "{{ device.displayName }} battery was {{ value }}%", translatable: true)
		}
	}

	return results
}


def off() {
	sendEvent(name: "switch", value: "off", isStateChange: true)
}


def on() {
	sendEvent(name: "switch", value: "on", isStateChange: true)
}


def setLevel(value, rate = null) {
	if (value == 0) {
		sendEvent(name: "switch", value: "off")
	} else {
		sendEvent(name: "switch", value: "on")
		sendEvent(name: "level", value: value)
	}
}


def installed() {
	sendEvent(name: "switch", value: "on")
	sendEvent(name: "level", value: 100)
}


def configure() {
	if (txtEnable) log.debug "Configure"
	//sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 10 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
	zigbee.readAttribute(0x0001, BATTERY_PERCENT_ATTR) +
	zigbee.configureReporting(0x0001 , BATTERY_PERCENT_ATTR, DataType.UINT8, 30, 10 * 60, null)
}


/**
 * Clear States
 *
 * Clears all device states
 *
**/
def ClearStates() {
	log.warn ("ClearStates(): Clearing device states")
	state.clear()
}


