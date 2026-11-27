# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Each version carries the targeted Minecraft version as build metadata, for example `1.0.0+26.3`.

## [Unreleased]

## [1.1.0+26.3] - 2026-11-27

### Added

- Show all settings in [OneConfig](https://modrinth.com/mod/oneconfig), which is optional, and keep them in sync with dglab's own screens, key bindings, and `/dglab` while the game runs. The settings are still saved only to `config/dglab.json`, so OneConfig profiles do not change them. The switch on dglab's card in OneConfig is the master switch.

### Changed

- Redraw the mod icon as line art: a heart with a lightning bolt, wearing a hair clip.

## [1.0.0+26.3] - 2026-11-15

The first release, for Minecraft 26.3. Read [Safety and risks](https://github.com/urntt/dglab#safety-and-risks) before you use it: the mod drives an electrical stimulation device, and so far it has only been tested against a simulated DG-LAB app, not a real device.

### Added

- Pair with the DG-LAB app by scanning a QR code shown in game. The mod hosts the connection server itself, on port 9999 by default.
- Raise the strength of both channels of a Coyote 3.0 when the player takes damage or dies, hold it, then let it decay down to a floor that grows with lost health. Each channel has its own settings.
- Add an output multiplier, from 0% to 1000%, that scales what damage, death, and the floor produce on both channels.
- Play a waveform after damage and another during recovery, chosen per channel from built-in waveforms or made in a waveform editor that also copies and pastes waveform code.
- Show both channels' strength, and optionally the limits set in the app, on a HUD while an app is connected. While none is, the pause menu says so.
- Add a two-player mode for worlds opened to LAN, in which a second player's health drives channel B.
- Add a key binding for every on/off setting, unbound by default, that flips the setting, saves it, and shows the new state on the action bar.
- Add an "Open dglab Settings" key binding, unbound by default, and a configuration screen built from vanilla widgets that Mod Menu, which is optional, can open too.
- Add a reset button next to every slider and a "Reset Page" button on every settings page.
- Add the `/dglab` client command for status, pairing, the connection server, strength, the output multiplier, the settings, and waveform tests.
- Set both channels to zero when the output or the connection server is switched off and when the player leaves the world.
- Save all settings to `config/dglab.json`.
- Add English and Simplified Chinese translations.
