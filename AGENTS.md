# AGENTS.md

Instructions for AI coding agents working in this repository. `CLAUDE.md` imports this file, so keep all agent guidance here.

## Project

`dglab` is a client-side [Fabric](https://fabricmc.net/) mod for Minecraft: Java Edition that reflects in-game experiences, such as taking damage or dying, to a DG-LAB Coyote 3.0 e-stim device through the DG-LAB app's socket control mode.

Its behavior is modeled on [CaiJi-ikun/DG_LAB](https://github.com/CaiJi-ikun/DG_LAB), which is licensed under GPL-3.0 while this project is MIT. That repository may be read to understand behavior, but never copy its source code, strings, or waveform data into this one.

The configuration screens follow the conventions of [urntt/nojumpdelay](https://github.com/urntt/nojumpdelay).

## Project decisions

These decisions are settled. Do not deviate from them without the user's explicit approval.

### Minecraft and toolchain

- Development started on Minecraft 26.3 with Java 25. `gradle.properties` is the single source of truth for the mod, Minecraft, Fabric Loader, Loom, Fabric API, Mod Menu, OneConfig, bundled library, and Java versions. `build.gradle`, `fabric.mod.json`, the mixin config, and the CI workflows read them from there; do not restate them elsewhere.
- Follow the latest official Fabric template ([FabricMC/fabric-example-mod](https://github.com/FabricMC/fabric-example-mod), also available from the [template generator](https://fabricmc.net/develop/template/)): the `net.fabricmc.fabric-loom` Gradle plugin, Mojang's official names with no `mappings` dependency, and `implementation` (not `modImplementation`) for dependencies. Do not use Yarn.
- Pin `loom_version` to a release version instead of the template's `-SNAPSHOT`, so builds are reproducible.
- Target only the latest stable (release) Minecraft version. Updates, fixes, and new features are always developed against it. Snapshots, pre-releases, and release candidates are not supported targets.
- Do not maintain older Minecraft versions and do not set up multi-version builds (no per-version branches, no Stonecutter or other preprocessors). When a new stable version is released, port the mod to it and drop the previous one.

### Identity

| Item | Value |
| --- | --- |
| Mod ID | `dglab` |
| Display name | `dglab` |
| Maven group | `com.urntt` |
| Base package | `com.urntt.dglab` |
| Version format | `<SemVer>+<Minecraft version>`, for example `1.0.0+26.3` |
| License | MIT |

The mod version itself follows [Semantic Versioning](https://semver.org/); the `+<Minecraft version>` suffix is build metadata naming the Minecraft version the build targets. Version numbers start at `1.0.0`.

### Distribution

- Releases are published only as GitHub Releases. Do not publish to Modrinth, CurseForge, or any other mod platform, and do not add publishing tooling for them.
- `README.md` must keep a clearly visible "Safety and risks" section. It covers physical safety (this mod drives an electrical stimulation device), software failure, multiplayer (other players and server events decide the output), the unencrypted LAN port, and the fact that the mod is unofficial and comes without warranty. The pairing screen and the message shown when a device binds repeat the short form of that warning.

### Scope and behavior

- Client-only: `fabric.mod.json` declares `"environment": "client"`. There is no Minecraft server component, the mod sends nothing to Minecraft servers, and it does not change gameplay.
- The mod talks to the DG-LAB app, not to the device: it hosts a WebSocket server that implements the app's socket control protocol, and the app connects by scanning a QR code shown in game. Bluetooth is out of scope.
- The terminal and app IDs used for pairing are random for every game start. One app is bound at a time; an app that binds with the right terminal ID replaces the one bound before, whose device is set to zero first.
- Only the local player's health drives the output. In two-player mode, which only works while this client hosts a world opened to LAN, the local player drives channel A and one named player drives channel B.
- `DgLabController` is the single owner of the output state: it combines the configuration, the connection, and the game events every client tick.
- Strength rules, per channel: damage adds strength and starts a hold period; after the hold the strength decays in steps down to a floor that scales with lost health; death raises the strength by a fixed amount. The strength never exceeds the limit set in the app.
- The output multiplier, a percentage from 0 to 1000, scales every strength the rules produce on both channels: what damage, death, and a leaving second player add, and the floor. Times and the decay step are not scaled.
- Waveforms: a damage waveform loops during the hold period and a recovery waveform loops while the strength decays. Each of the four slots (channel A or B, damage or recovery) holds either a built-in preset or a custom waveform made in the waveform editor.
- The HUD shows the strengths only while an app is bound. Otherwise it stays empty, and the pause menu shows the state of the connection ("not connected", "port unavailable") at the HUD's position. The HUD's on/off setting hides both.
- Safety behavior: turning the master switch off, leaving the world, or stopping the connection server sets both channels to zero and clears the queued waveforms.
- Every on/off setting is a `ToggleSetting`. Each has an entry on the configuration screen and its own key binding, unbound by default; pressing it flips the setting, saves it, and shows the new state on the action bar. Numeric settings have no key binding.
- A further key binding, also unbound by default, opens the configuration screen. Mod Menu and the `/dglab settings` command open it too.
- With OneConfig installed, its menu shows every setting, including the connection settings; only the pairing screen and the waveform editor stay in the mod's own screens, which buttons on OneConfig's page open with OneConfig as their parent. OneConfig is a live view of `DgLabConfig`, never a second store:
  - The tree is UI-only (`Backend.UI_ONLY_METADATA`), so OneConfig neither loads nor saves it and its profiles do not apply. Its ID must differ from `dglab.json`, because OneConfig's profile folder is the game's config directory.
  - Every option is a functional property that reads and writes `DgLabConfig` and carries the mod's default as `"default"` metadata, which OneConfig's reset uses.
  - `DgLabConfig` notifies its listeners after every save, and the tree then calls `setReferential` on every option so that OneConfig's widgets update. A guard keeps those calls from writing back.
  - A listen port typed in OneConfig is applied one second after the last change, so the server does not restart for every digit.
  - The switch on the mod's card in OneConfig (`oneconfig:toggle`) is the master switch.
- Every change is saved to the configuration file immediately.
- Resetting follows vanilla's key binds screen: every slider has its own "Reset" button, disabled while the slider is at its default, and every settings page has a "Reset Page" button in its footer for everything on that page. Pages that save immediately extend `DgLabOptionsScreen`, which provides both; on the pages with "Done" and "Cancel", a reset only fills in the defaults. The samples in the waveform editor are data without defaults, so the editor only has the page button, which loads the slot's default preset.
- The `/dglab` client command exposes status, pairing, the connection server, strength, the output multiplier, the toggles, the per-channel numbers, and waveform tests.

### Localization

- All user-facing text, including key binding names, the key binding category, action bar messages, command feedback, the HUD, and the configuration screens, uses translation keys. Never hard-code display strings.
- Provide translations for `en_us` and `zh_cn`, and keep both complete whenever a translation key is added or changed.

### Dependencies

- Required: Fabric Loader and Fabric API.
- Optional: Mod Menu, declared under `suggests` in `fabric.mod.json`. The mod must load and work normally without it, so Mod Menu classes may only be referenced from the Mod Menu entrypoint.
- Optional: OneConfig, also under `suggests`, approved by the user. Its classes may only be referenced from `com.urntt.dglab.compat.oneconfig`, whose classes only OneConfig's entrypoints (`oneconfig:config` and `oneconfig:toggle`) load.
  - Its API (`config`, `config-impl`, and `mod-toggles` from the Polyfrost repository) is compile-only and never shipped. OneConfig is LGPL-3.0 with additional terms, so it is neither bundled nor modified, and the mod stays MIT.
  - OneConfig, PolyCompose, and Fabric Language Kotlin are `localRuntime` dependencies, so only the development game and the tests run them. OneConfig and PolyCompose come from Modrinth's Maven by version ID, because their version numbers are shared with the builds for other platforms. `-Pdglab.oneconfig=false` leaves all three out.
- Bundled: [`io.nayuki:qrcodegen`](https://www.nayuki.io/page/qr-code-generator-library) (MIT), included in the mod jar, draws the pairing QR code. It is the one approved exception to the no-extra-dependencies rule; it has no dependencies of its own and does not depend on Minecraft.
- The WebSocket server uses the Netty HTTP codec that Minecraft bundles. Do not add a WebSocket library.
- Test only: ZXing is a dependency of the `gametest` source set, used to decode the generated QR code. It must not be shipped.
- Configuration is hand-written without a config library: a JSON file in the Fabric config directory, serialized with Gson (bundled with Minecraft). Configuration screens use vanilla widgets.
- Do not add other dependencies without the user's explicit approval.

### Implementation

- Language: Java only.
- Source sets: `src/main` holds only `fabric.mod.json` and the icon. All code and client resources live in `src/client`, and the client game tests live in `src/gametest`.
- The icon is drawn in `assets/dglab/icon.svg`, its single source. `icon.png`, which Fabric, Mod Menu, and OneConfig read, is rendered from it at 128×128 with the command in `README.md`. Edit the SVG, never the PNG, and render the PNG again in the same change.
- Mixins: prefer the MixinExtras injectors bundled with Fabric Loader (for example `@ModifyExpressionValue` and `@WrapOperation`) over `@Redirect` and `@Overwrite`, to stay compatible with other mods and keep porting work small.
- The only mixin observes `LocalPlayer.hurtTo`, which receives every health value the server sends. It remembers the previous health itself: by the time `hurtTo` runs, the player's health has usually been updated through its synced entity data already, so comparing with `getHealth()` finds no damage.
- `feedback.StrengthController` and the `protocol` and `waveform` packages must not reach into the running game (no `Minecraft`, world, or player access), so that the rules can be tested without a world. `DgLabController` and `SecondPlayerMonitor` do that part.
- The connection server reports its events through an executor that queues (`Minecraft.schedule`), so the controller sees them on the client thread in the order they happened.

### Testing

- The client game tests in `src/gametest` start Minecraft and check the mod against `FakeDgLabApp`, a stand-in for the DG-LAB app that speaks the socket protocol over a real WebSocket. Keep them passing and extend them when behavior changes.
  - `DgLabLogicGameTest` needs no world: the protocol, waveforms, translations, the configuration file, the strength rules tick by tick, the two-player rules, the output multiplier, the connection server, and the QR code, which it decodes with ZXing.
  - `DgLabClientGameTest` plays in a singleplayer world: the pause menu's connection state, damage, hold, decay and floor, death and respawn, every key binding, the commands, the reset buttons, every screen in both languages, the QR code read back from a screenshot, the waveform editor, and leaving the world.
  - `DgLabOneConfigGameTest` checks the OneConfig integration when OneConfig is installed and only logs that it skipped otherwise: that OneConfig stores nothing, changes in both directions, the defaults, the listen port delay, the card switch, the buttons, and screenshots of OneConfig, opened with its default key, in both languages. Its checks live in `OneConfigChecks`, which only it loads, because that class refers to OneConfig.
  - `DgLabMultiplayerGameTest` checks damage on a local dedicated server. That server needs `eula = true` in the `configureTests` block of `build.gradle`; it accepts the Minecraft EULA only for that local test server.
- Each test class sets the configuration it relies on, because the classes share the game's config file.
- The tests run with OneConfig installed by default. Run them with `-Pdglab.oneconfig=false` as well whenever a change could depend on OneConfig being present; CI runs both.
- When a test fails, Fabric Loader opens an error window and the game waits until it is closed, unless the `CI` environment variable or the `fabric.noGui` system property is set. Set `CI=true` when running the tests on a headless machine.
- No real device is available to the tests, and neither is a second player, so two-player mode is only covered at the level of its rules. Changes to the protocol or the output must be reported to the user as needing a check on real hardware.
- After porting to a new Minecraft version, run the client game tests. A successful build does not prove that the mixins still have the intended effect.
- `README.md` describes how to run them, including on a headless machine.

### CI, releases, and changelog

- GitHub Actions (`.github/workflows/build.yml`) builds the project and runs the client game tests on every push and pull request, once with OneConfig and once without it.
- Maintain `CHANGELOG.md` following [Keep a Changelog](https://keepachangelog.com/). Record every user-visible change under `Unreleased` in the same change that introduces it.
- `.github/workflows/release.yml` builds the mod and publishes a GitHub Release for the project version, with the jar attached and the matching `CHANGELOG.md` section as release notes. It runs when a tag `v<version>` (for example `v1.0.0+26.3`) is pushed, or when started manually on a branch, in which case it creates that tag on the branch's latest commit. It fails if a pushed tag does not match the project version, if the changelog has no section for the version, or if the release already exists.
- Release only when the user asks. To release, set `mod_version` in `gradle.properties`, rename `Unreleased` in `CHANGELOG.md` to `[<version>] - <YYYY-MM-DD>` above a new empty `Unreleased` section, commit, and push. Then start the release workflow on `main`. Claude Code cloud sessions cannot push tags, so start the workflow through the GitHub Actions API instead.

## Engineering principles

- Fix root causes, not symptoms. Diagnose the underlying cause before implementing a permanent fix. If an immediate mitigation is necessary, treat it as temporary and follow through with a root-cause fix.
- Prefer configuration-driven design for values that are expected to vary by environment, deployment, or product requirements. Avoid unexplained or duplicated magic values, but do not introduce configuration where a well-named constant is the clearer source of truth.
- Preserve a single source of truth and clear ownership for data, state, configuration, business logic, and authoritative documentation. Avoid duplicating canonical information across multiple locations.
- Do not maintain parallel legacy and replacement implementations without an explicit migration and removal plan.

## Documentation

- Keep documentation aligned with the code. When a code change affects documented behavior, APIs, architecture, configuration, workflows, or usage, update the relevant documentation in the same change.
- Keep each document's responsibility clear. For example, use `README.md` for project overview and usage, and `VISION.md` for product direction, architectural principles, or long-term decisions.
- Always specify a language identifier for fenced code blocks in Markdown.

## Language

- Communicate with the user in Chinese, including explanations, progress updates, and user-facing planning.
- Use English for development artifacts, including source code, comments, docstrings, documentation, READMEs, Git branch names, commit messages, and other deliverables intended to live in the repository.

## Git

- Do not change or override the Git author or committer identity. When an identity must be configured for commits created during the task, use:
  - Name: `urntt`
  - Email: `urntts@gmail.com`
- Do all actions on the user's behalf. Do not rewrite existing commit authorship unless explicitly requested. Do not add `Co-Authored-By` trailers or session links to commit messages or pull request descriptions.
- Develop on `main` and push directly to it. Branches and pull requests are not required.
- Because changes land on `main` without review, make sure `./gradlew build` and the client game tests pass locally before pushing.
- If a branch is used, give it a category-based prefix that reflects the purpose of the change, such as `feat/`, `fix/`, `refactor/`, `docs/`, `test/`, or `chore/`.
- Follow the [Conventional Commits](https://www.conventionalcommits.org/) specification for commit messages.
