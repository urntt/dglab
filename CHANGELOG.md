# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Each version carries the targeted Minecraft version as build metadata, for example `1.0.0+26.3`.

## [Unreleased]

### Added

- Pair with the DG-LAB app by scanning a QR code shown in game. The mod hosts the connection server itself, on port 9999 by default.
- Raise the strength of both channels of a Coyote 3.0 when the player takes damage or dies, hold it, then let it decay down to a floor that grows with lost health. Each channel has its own settings.
- Play a waveform after damage and another during recovery, chosen per channel from built-in waveforms or made in a waveform editor that also copies and pastes waveform code.
- Show both channels' strength, and optionally the limits set in the app, on a HUD.
- Add a two-player mode for worlds opened to LAN, in which a second player's health drives channel B.
- Add a key binding for every on/off setting, unbound by default, that flips the setting, saves it, and shows the new state on the action bar.
- Add an "Open dglab Settings" key binding, unbound by default, and a configuration screen built from vanilla widgets that Mod Menu, which is optional, can open too.
- Add the `/dglab` client command for status, pairing, the connection server, strength, the settings, and waveform tests.
- Set both channels to zero when the output or the connection server is switched off and when the player leaves the world.
- Save all settings to `config/dglab.json`.
- Add English and Simplified Chinese translations.
