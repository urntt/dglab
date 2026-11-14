# dglab

A simple client-side Fabric mod for Minecraft: Java Edition that reflects in-game experiences to DG-LAB devices.

When your player takes damage or dies, the mod raises the output strength of a DG-LAB Coyote 3.0 through the DG-LAB app's socket control mode, then lets it decay again.

## Safety and risks

**This mod drives an electrical stimulation device from game events. Read this section before you use it.**

- **Physical safety.** Read and follow the manual of your DG-LAB device first. Never place electrodes on the chest or near the heart, on the head, or on the neck. Do not use the device if you have a pacemaker or another implanted electronic device, a heart condition, or epilepsy, or if you are pregnant. Set the strength limits in the DG-LAB app before you play and start low: the mod never exceeds those limits, but within them the strength can rise suddenly, for example after an explosion, a fall, or a death. The mod's master switch sets both channels to zero, but the controls and the power button of the device itself are always the last resort.
- **Software failure.** If the game freezes or crashes, or the network connection drops, the strength can stay where it was. Lower it in the app or on the device.
- **Multiplayer.** The mod sends nothing to Minecraft servers and does not change gameplay. However, other players and server events hurt your player and therefore decide your output strength.
- **Network.** The mod opens an unencrypted WebSocket port on your local network for the DG-LAB app to connect to. Use it only on networks you trust and never forward the port to the internet.
- **Unofficial.** This project is not affiliated with or endorsed by DG-LAB. It is provided under the MIT license without warranty of any kind; you use it at your own risk.

## License

[MIT](LICENSE)
