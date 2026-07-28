/*
 * WiiM HTTPS Volume Controller
 *
 * Version: 0.1.0
 * Author: backd00rbandit
 * License: MIT
 *
 * Tested with:
 * - Hubitat Elevation: 2.5.0.159
 * - WiiM Ultra firmware: 5.2.818432
 *
 * This is an unofficial community driver and is not affiliated with
 * WiiM, Linkplay, or Hubitat.
 */

metadata {
    definition(
        name: "WiiM HTTPS Volume Controller",
        namespace: "backd00rbandit",
        author: "backd00rbandit",
        singleThreaded: true
    ) {
        capability "Actuator"
        capability "AudioVolume"
        capability "Refresh"
    }

    preferences {
        input name: "ipAddress",
            type: "text",
            title: "WiiM IP address",
            description: "IP only, for example 192.168.1.75",
            required: true

        input name: "volumeStep",
            type: "number",
            title: "Volume up/down step",
            range: "1..20",
            defaultValue: 5,
            required: true

        input name: "debugLogging",
            type: "bool",
            title: "Enable debug logging",
            defaultValue: true
    }
}

void installed() {
    log.info "${device.displayName} installed"
}

void updated() {
    log.info "${device.displayName} settings updated"

    if (settings.debugLogging == true) {
        runIn(1800, "disableDebugLogging")
    }

    if (settings.ipAddress) {
        refresh()
    }
}

void setVolume(volumeLevel) {
    Integer level = normalizeVolume(volumeLevel)

    if (sendWiiMCommand("setPlayerCmd:vol:${level}")) {
        sendEvent(
            name: "volume",
            value: level,
            unit: "%",
            descriptionText: "${device.displayName} volume set to ${level}%"
        )
    }
}

void mute() {
    if (sendWiiMCommand("setPlayerCmd:mute:1")) {
        sendEvent(
            name: "mute",
            value: "muted",
            descriptionText: "${device.displayName} muted"
        )
    }
}

void unmute() {
    if (sendWiiMCommand("setPlayerCmd:mute:0")) {
        sendEvent(
            name: "mute",
            value: "unmuted",
            descriptionText: "${device.displayName} unmuted"
        )
    }
}

void volumeUp() {
    adjustVolume(getVolumeStep())
}

void volumeDown() {
    adjustVolume(-getVolumeStep())
}

void refresh() {
    if (!settings.ipAddress) {
        log.warn "${device.displayName}: WiiM IP address has not been configured"
        return
    }

    Map requestParams = buildRequest("getPlayerStatus")

    try {
        httpGet(requestParams) { response ->
            String responseBody = response.data?.toString()

            if (settings.debugLogging == true) {
                log.debug "${device.displayName}: getPlayerStatus returned ${responseBody}"
            }

            Map status = parseJson(responseBody) as Map

            if (status.vol != null) {
                Integer currentVolume = normalizeVolume(status.vol)

                sendEvent(
                    name: "volume",
                    value: currentVolume,
                    unit: "%"
                )
            }

            if (status.mute != null) {
                String muteState =
                    status.mute.toString() == "1" ? "muted" : "unmuted"

                sendEvent(
                    name: "mute",
                    value: muteState
                )
            }
        }
    }
    catch (Exception exception) {
        log.error "${device.displayName}: Unable to read WiiM status: ${exception.message}"
    }
}

private void adjustVolume(Integer adjustment) {
    /*
     * Refresh synchronously so volume changes made in the WiiM app
     * are considered before calculating the new value.
     */
    refresh()

    def currentValue = device.currentValue("volume")

    if (currentValue == null) {
        log.error "${device.displayName}: Current volume is unavailable"
        return
    }

    Integer newVolume =
        normalizeVolume(new BigDecimal(currentValue.toString()).intValue() + adjustment)

    setVolume(newVolume)
}

private Boolean sendWiiMCommand(String command) {
    if (!settings.ipAddress) {
        log.error "${device.displayName}: WiiM IP address has not been configured"
        return false
    }

    try {
        Map requestParams = buildRequest(command)

        httpGet(requestParams) { response ->
            if (settings.debugLogging == true) {
                log.debug "${device.displayName}: ${command} returned HTTP ${response.status}: ${response.data}"
            }
        }

        return true
    }
    catch (Exception exception) {
        log.error "${device.displayName}: WiiM command failed: ${exception.message}"
        return false
    }
}

private Map buildRequest(String command) {
    String host = settings.ipAddress
        .toString()
        .trim()
        .replaceFirst(/^https?:\/\//, "")
        .replaceAll(/\/+$/, "")

    return [
        uri: "https://${host}/httpapi.asp?command=${command}",
        ignoreSSLIssues: true,
        textParser: true,
        timeout: 10
    ]
}

private Integer normalizeVolume(def value) {
    Integer level

    try {
        level = new BigDecimal(value.toString()).intValue()
    }
    catch (Exception ignored) {
        throw new IllegalArgumentException(
            "Volume must be a number between 0 and 100"
        )
    }

    return Math.max(0, Math.min(100, level))
}

private Integer getVolumeStep() {
    try {
        return Math.max(
            1,
            Math.min(
                20,
                new BigDecimal(settings.volumeStep?.toString() ?: "5").intValue()
            )
        )
    }
    catch (Exception ignored) {
        return 5
    }
}

void disableDebugLogging() {
    log.warn "${device.displayName}: Disabling debug logging"
    device.updateSetting(
        "debugLogging",
        [value: "false", type: "bool"]
    )
}
