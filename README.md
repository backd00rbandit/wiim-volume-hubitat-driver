# wiim-volume-hubitat-driver

# WiiM HTTPS Volume Controller for Hubitat

A lightweight Hubitat custom driver for controlling volume and mute state on a WiiM player through WiiM’s local HTTPS API.

This driver was created because current WiiM firmware exposes the local API over HTTPS using a self-signed certificate. The API works with tools such as `curl -k`, but Hubitat Rule Machine’s built-in HTTP GET action does not provide an option to bypass certificate-validation errors.

The driver sends the request from Hubitat using:

```groovy
ignoreSSLIssues: true
```

It then exposes the WiiM player as a Hubitat device with standard volume and mute commands.

## Tested Configuration

This driver has been tested with:

| Component        | Tested version |
| ---------------- | -------------- |
| Hubitat hardware | C-5            |
| Hubitat firmware | 2.5.0.159      |
| WiiM hardware    | WiiM Ultra     |
| WiiM firmware    | 5.2.818432     |

Other Hubitat hubs, Hubitat firmware versions, and WiiM products may work, but have not yet been confirmed.

## Features

* Set volume from 0–100
* Increase or decrease volume
* Configurable volume adjustment step
* Mute
* Unmute
* Refresh the current volume and mute state
* Standard Hubitat `AudioVolume` capability
* Rule Machine support through a custom `Actuator` action
* Local network control
* No WiiM cloud account or API credentials required

## Requirements

* A Hubitat Elevation hub
* A WiiM player reachable from the Hubitat hub over the local network
* A reserved or static IP address for the WiiM player
* Network and firewall rules permitting HTTPS traffic from Hubitat to the WiiM

Do not expose the WiiM local API or Hubitat hub directly to the Internet.

## Installation

1. Open the Hubitat administration interface.
2. Go to **Drivers Code**.
3. Select **New Driver**.
4. Paste the contents of the driver `.groovy` file.
5. Click **Save**.
6. Go to **Devices**.
7. Select **Add Device**.
8. Create a new virtual device.
9. Choose **WiiM HTTPS Volume Controller** as the device type.
10. Open the newly created device.
11. Enter the WiiM player’s local IP address in the device preferences.
12. Configure the preferred volume step.
13. Click **Save Preferences**.
14. Click **Refresh**.

After refreshing, the current volume and mute state should appear under the device’s current states.

## Device Preferences

### WiiM IP address

Enter only the local IP address of the WiiM player.

Example:

```text
192.168.1.75
```

Do not include `http://`, `https://`, or a URL path.

The WiiM should have a DHCP reservation or static IP address. If its address changes, the driver will stop communicating with it.

### Volume step

Controls how much the volume changes when `volumeUp` or `volumeDown` is called.

For example, a step of `5` changes the volume from 20 to 25 or from 20 to 15.

### Debug logging

When enabled, the driver records API requests and responses in the Hubitat logs.

Debug logging automatically disables itself after 30 minutes.

## Testing the Driver

From the Hubitat device page:

1. Click **Refresh**.
2. Confirm that `volume` and `mute` appear under the current device states.
3. Run **Set Volume** with a value such as `25`.
4. Confirm that the WiiM volume changes.
5. Test **Mute**, **Unmute**, **Volume Up**, and **Volume Down**.

Volume values are limited to the range `0` through `100`.

## Rule Machine Configuration

The tested method is to invoke `setVolume` as a custom action using the device’s **Actuator** capability.

In Rule Machine:

1. Add an action.
2. Select **Run Custom Action**.
3. Select the **Actuator** capability.
4. Select the WiiM virtual device.
5. Choose the `setVolume` command.
6. Add one parameter.
7. Set the parameter type to **Number**.
8. Enter a value from `0` through `100`.

Example:

```text
Run Custom Action
Capability: Actuator
Device: WiiM Ultra
Command: setVolume
Parameter type: Number
Parameter value: 25
```

Although the driver implements Hubitat’s `AudioVolume` capability, the device may not appear under `AudioVolume` in Rule Machine’s custom-action selector. Selecting **Actuator** was required in the tested configuration.

Other available custom commands include:

* `setVolume`
* `volumeUp`
* `volumeDown`
* `mute`
* `unmute`
* `refresh`

Commands without an argument do not require a parameter.

## How It Works

To set the volume, the driver sends a local HTTPS GET request such as:

```text
https://WIIM-IP/httpapi.asp?command=setPlayerCmd:vol:25
```

To obtain the current state, it sends:

```text
https://WIIM-IP/httpapi.asp?command=getPlayerStatus
```

Current WiiM firmware uses a self-signed HTTPS certificate for this interface. The driver therefore configures Hubitat’s HTTP client with:

```groovy
ignoreSSLIssues: true
```

This certificate-validation bypass applies only to requests made by this driver to the configured WiiM IP address.

## Manual API Testing

Before troubleshooting the Hubitat driver, confirm that the WiiM API responds from another device on the same network.

### Read player status

```bash
curl -k "https://WIIM-IP/httpapi.asp?command=getPlayerStatus"
```

### Set volume

```bash
curl -k "https://WIIM-IP/httpapi.asp?command=setPlayerCmd:vol:25"
```

Replace `WIIM-IP` with the player’s local IP address.

The `-k` option allows `curl` to accept the WiiM’s self-signed certificate.

## Troubleshooting

### The device does not respond

Verify that:

* The WiiM IP address is correct.
* The WiiM is powered on and connected to the network.
* The Hubitat hub and WiiM can communicate across any VLANs or subnets.
* HTTPS traffic on TCP port 443 is permitted between the devices.
* The WiiM IP address has not changed.
* The manual `curl -k` test succeeds.

### Refresh does not populate the device state

Enable debug logging, click **Refresh**, and review **Logs** in Hubitat.

Confirm that the API returns a response containing volume and mute values.

### The device does not appear in Rule Machine

When configuring a custom action, select the **Actuator** capability rather than `AudioVolume`.

### Volume Up or Volume Down uses an incorrect starting value

Click **Refresh** before testing. The driver uses the most recently retrieved volume state when calculating an adjustment.

### The browser reports a certificate warning

This is expected. WiiM uses a self-signed certificate for the local HTTPS API.

The driver handles this using Hubitat’s `ignoreSSLIssues` HTTP option.

## Security Notes

* Communication occurs directly between Hubitat and the configured WiiM IP address.
* No WiiM username, password, token, or cloud account is used.
* The WiiM local API does not require authentication for these commands.
* Any device with network access to the WiiM API may potentially issue similar commands.
* Network segmentation or firewall rules should be used where appropriate.
* Neither Hubitat nor the WiiM API should be exposed directly to the public Internet.

## Project Scope

This is intentionally a small volume-control driver rather than a complete WiiM integration.

It currently does not provide:

* Playback browsing
* Music-service integration
* Media metadata
* Album artwork
* Source selection
* Input switching
* Multiroom grouping
* Device discovery
* Firmware management
* Full transport control

Additional functionality may be added later, but the initial goal is reliable local volume control from Hubitat automations.

## Compatibility Reports

Reports from users testing other WiiM products or Hubitat models are welcome.

When reporting compatibility or a problem, include:

* Hubitat hardware model
* Hubitat firmware version
* WiiM hardware model
* WiiM firmware version
* Relevant Hubitat log output
* Whether the manual `curl -k` request succeeds

Do not include public IP addresses, account credentials, or other sensitive information.

## Disclaimer

This is an unofficial community project.

It is not affiliated with, endorsed by, or supported by WiiM, Linkplay, or Hubitat.

Use at your own risk.

## License

MIT License
