# dglab

A simple client-side Fabric mod for Minecraft: Java Edition that reflects in-game experiences to DG-LAB devices.

When your player takes damage or dies, the mod raises the output strength of a DG-LAB Coyote 3.0 through the DG-LAB app's socket control mode, then lets it decay again. The mod talks to the app on your phone over your local network; the app keeps talking to the device over Bluetooth.

## Safety and risks

**This mod drives an electrical stimulation device from game events. Read this section before you use it.**

- **Physical safety.** Read and follow the manual of your DG-LAB device first. Never place electrodes on the chest or near the heart, on the head, or on the neck. Do not use the device if you have a pacemaker or another implanted electronic device, a heart condition, or epilepsy, or if you are pregnant. Set the strength limits in the DG-LAB app before you play and start low: the mod never exceeds those limits, but within them the strength can rise suddenly, for example after an explosion, a fall, or a death. Turning **DG-LAB Output** off sets both channels to zero, but the controls and the power button of the device itself are always the last resort.
- **Software failure.** If the game freezes or crashes, or the network connection drops, the strength can stay where it was. Lower it in the app or on the device.
- **Multiplayer.** The mod sends nothing to Minecraft servers and does not change gameplay. However, other players and server events hurt your player and therefore decide your output strength.
- **Network.** The mod opens an unencrypted WebSocket port on your local network for the DG-LAB app to connect to. Use it only on networks you trust and never forward the port to the internet.
- **Unofficial.** This project is not affiliated with or endorsed by DG-LAB. It is provided under the MIT license without warranty of any kind; you use it at your own risk.

Only a simulated app was available while developing this mod. Treat the first session with a real device as a test: use low limits.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Download the jar from this repository's [Releases](https://github.com/urntt/dglab/releases) page. Each release supports a single Minecraft version, shown after the `+` in its version number. For example, `1.0.0+26.3` is for Minecraft 26.3.
3. Put the jar into your `.minecraft/mods` folder.

[Mod Menu](https://modrinth.com/mod/modmenu) is optional. When installed, it opens the mod's configuration screen from its mod list.

[OneConfig](https://modrinth.com/mod/oneconfig) is optional too. When installed, it shows all of dglab's settings in its own menu; see [OneConfig](#oneconfig).

You also need a DG-LAB Coyote 3.0 and the DG-LAB app on a phone that is on the same network as your computer.

## Pairing

1. Connect the Coyote to the DG-LAB app as usual and set the strength limits of both channels in the app.
2. In Minecraft, open the mod's configuration screen and choose **Pair Device...**. The screen shows a QR code.
3. In the DG-LAB app, open **SOCKET control** and scan the code. The pairing screen shows when the app is connected, and the HUD then shows both channels' strength. While no app is connected, the HUD stays empty and the pause menu says "DG-LAB: not connected".

The first time, your operating system may ask whether Minecraft may accept network connections; allow it for private networks. If the phone cannot connect, the QR code may point to the wrong network: choose the right address under **Connection Settings...**.

The code is different after every game start, so the app has to scan it again. One app is connected at a time: an app that scans the code takes the place of the one connected before.

## Usage

### What the mod does

Each of the device's two channels follows the same rules, with its own numbers:

- **Damage** adds strength: the damage in points (half hearts) times a factor, rounded down, but at least 1.
- After damage, the strength is **held** for a while. More damage restarts the hold time.
- Then the strength **decays** in steps, down to a **floor** that grows with the share of health you have lost. At full health the floor is zero.
- **Death** adds a fixed amount on top.
- A **damage waveform** plays during the hold time, and a **recovery waveform** while strength remains.

Leaving the world, turning **DG-LAB Output** off, or stopping the connection server sets both channels to zero.

### Key bindings

All key bindings are in **Options → Controls → Key Binds** and are unbound by default.

- **Open dglab Settings** opens the configuration screen. With Mod Menu installed, you can also open it from the mod list.
- Every on/off setting has its own key, named **Toggle** followed by the setting's name. Pressing it flips the setting, saves it, and shows the new state on the action bar.

### Settings

All settings are saved to `config/dglab.json` as soon as you change them.

Every slider has a **Reset** button next to it that puts it back to its default, and every settings page has a **Reset Page** button that does so for everything on that page. On the pages that have **Done** and **Cancel** buttons, a reset is saved by **Done** like any other change there. Key bindings are reset in the game's own key binds screen.

On/off settings, each with a toggle key:

| Setting | Default | Meaning |
| --- | --- | --- |
| DG-LAB Output | On | The master switch. While it is off, both channels stay at zero and game events are ignored. |
| Connection Server | On | Whether the server the app connects to is running. It starts with the game. |
| Channel A, Channel B | On | Whether the channel reacts to game events. Turning one off sets it to zero. |
| Damage Response | On | Whether damage raises the strength. |
| Death Response | On | Whether death raises the strength. |
| Low Health Floor | On | Whether the strength stops decaying at the floor. |
| Strength HUD | On | Whether both channels' strength is shown on screen while an app is connected. Without an app, the pause menu says so instead. |
| Show Limits in HUD | Off | Whether the HUD also shows the limits set in the app. |
| Two-Player Mode | Off | See below. |

Strength settings, separate for each channel:

| Setting | Default | Range | Meaning |
| --- | --- | --- | --- |
| Strength per Damage Point | 3.0 | 0 to 20 | Strength added per point of damage. At 0, damage is ignored. |
| Hold After Damage | 2.5 s | 0 to 6 s | How long the strength stays up before it decays. |
| Decay Interval | 0.25 s | 0.05 to 6 s | Time between two decay steps. |
| Decay Step | 1 | 0 to 20 | Strength removed per step. At 0, the strength never decays. |
| Strength Added on Death | 50 | 0 to 200 | Added on death, on top of the fatal damage. |
| Hold After Death | 2.5 s | 0 to 6 s | How long the strength stays up after death. |
| Low Health Floor | 40 | 0 to 200 | The floor when all health is lost. At half health the floor is half of it. |

Other settings:

- **Output Multiplier** scales the strength that damage, death, and the floor produce on both channels, from 0% to 1000%. It is 100% by default, and at 0% game events produce no output. The slider moves in steps of 1%; use the arrow keys on the selected slider or `/dglab multiplier` for an exact value. However large it is, the strength stays within the limits set in the app.
- **HUD X, HUD Y** place the HUD, from one screen edge (0%) to the opposite one (100%).
- **Connection Settings...** changes the port the server listens on (9999 by default) and the address and port in the QR code.
- **Waveforms...** chooses the waveform of each channel after damage and during recovery: a built-in one or **Custom**. **Edit Custom Waveform...** opens an editor with one row per 25 ms. A row is either set by hand or interpolated between the nearest hand-set rows. The editor copies and pastes waveform code in the hexadecimal form other DG-LAB tools use. A waveform is at most 8 seconds long.

### OneConfig

With [OneConfig](https://modrinth.com/mod/oneconfig) installed, dglab appears in OneConfig's mod list, which opens with Right Shift by default. Its page has every setting described above, including the connection settings. The **Pair Device...** and **Waveforms...** buttons there open dglab's own screens, and **Done** on those screens returns to OneConfig.

OneConfig and dglab stay in sync while the game runs: a change made in OneConfig is saved to `config/dglab.json` at once, and a change made in dglab's own screens, with a toggle key, or with `/dglab` shows in OneConfig. Some details:

- `config/dglab.json` stays the only place dglab's settings are saved. OneConfig keeps no copy of them, so switching OneConfig profiles does not change them.
- A new listen port takes effect one second after you stop typing it, so the server does not restart for every digit. A QR code address that is not a valid host name or IP address is not saved.
- Switching dglab off or on with the toggle on its card in OneConfig's mod list flips the **DG-LAB Output** master switch.
- OneConfig's reset buttons put settings back to dglab's defaults.
- While OneConfig's menu is open, its switches follow changes made elsewhere at once. Its sliders, number and text fields, and drop-down lists show such changes the next time the page is opened.
- dglab's key binding, `/dglab settings`, and Mod Menu still open dglab's own configuration screen, as does **Original menu** on dglab's page in OneConfig.

### Two-player mode

Two-player mode lets a second player drive channel B. It only works while you host a world opened to LAN, because only the host knows the other player's health. While the player named under **Two-Player Settings...** is in your world, your health drives channel A and theirs drives channel B. Optionally, channel B gains strength when that player leaves the world alive.

### Commands

The `/dglab` client command offers the same functions from the chat:

| Command | Effect |
| --- | --- |
| `/dglab` or `/dglab status` | Shows the state of the connection and both channels. |
| `/dglab settings`, `/dglab pair` | Opens the configuration screen or the pairing screen. |
| `/dglab server start`, `/dglab server stop` | Starts or stops the connection server. |
| `/dglab strength get`, `/dglab strength set <a> <b>` | Shows or sets both channels' strength. |
| `/dglab multiplier`, `/dglab multiplier <percent>` | Shows or sets the output multiplier. |
| `/dglab toggle <setting>` | Flips an on/off setting. |
| `/dglab config get`, `/dglab config set <option> <a> <b>` | Shows the strength settings, or sets one for both channels. Times are in ticks. |
| `/dglab test <slot>` | Plays a waveform slot once. |

## Development

Building requires the JDK version set by `java_version` in `gradle.properties`.

Build the mod:

```bash
./gradlew build
```

The jar is written to `build/libs/`.

Run the client game tests. They start Minecraft and connect a simulated DG-LAB app to the mod, then check the protocol, the strength rules, the key bindings, the commands, and the screens, in a singleplayer world and on a local dedicated server:

```bash
./gradlew runClientGameTest
```

The game tests need a display. On a headless Linux machine, run them under Xvfb. Xvfb offers no sRGB-capable OpenGL visuals, so install Mesa's Vulkan driver (`mesa-vulkan-drivers` on Ubuntu) for the game to fall back to:

```bash
xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew runClientGameTest
```

Screenshots taken by the tests are saved to `build/run/clientGameTest/screenshots/`.

When a test fails, Fabric Loader opens an error window and the game waits until it is closed. On a headless machine, set the `CI` environment variable to skip the window, as CI services do:

```bash
CI=true xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew runClientGameTest
```

The multiplayer test starts a local dedicated server, so the test setup in `build.gradle` accepts the [Minecraft EULA](https://aka.ms/MinecraftEULA) for that test server.

The tests run with [OneConfig](#oneconfig) installed, which `build.gradle` adds to the development game together with the mods it needs; none of them is included in the mod's jar. To run them without OneConfig, which checks that the mod works without it, add `-Pdglab.oneconfig=false`:

```bash
./gradlew runClientGameTest -Pdglab.oneconfig=false
```

The tests cannot reach a real device. Changes to the protocol or to what is sent need a check with real hardware.

## License

[MIT](LICENSE)

The mod jar bundles the [QR Code generator library](https://www.nayuki.io/page/qr-code-generator-library) by Project Nayuki, also under the MIT license.

The behavior is modeled on the [DG_LAB](https://github.com/CaiJi-ikun/DG_LAB) mod by CaiJi-ikun; no code is shared with it.
