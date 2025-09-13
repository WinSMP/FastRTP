# FastRTP

> Simple and insanely fast async /rtp plugin. Fast on any kind of server, from lowest-end to high-end

FastRTP is a plugin for Paper and Folia that allows players to teleport to random safe locations within a specified range. This plugin is designed to provide a fun and exciting way for players to explore their Minecraft world.

## What's with the "Fast" in the name?

FastRTP is designed to be fast and efficient, even on low-end servers. It achieves this by using a combination of techniques, including:

* **Asynchronous teleportation:** When a player uses `/rtp`, the plugin doesn't freeze the server while it searches for a safe location. Instead, it performs the search in the background, allowing the server to continue running smoothly. This means that even if the server is under heavy load, players can still teleport without causing lag.
* **Location preloading:** FastRTP preloads safe locations in the background, so that when a player uses `/rtp`, a safe location is already available. This means that players can teleport almost instantly, without having to wait for the plugin to find a safe location. The only unavoidable overhead here is the player requesting the surrounding chunks.

## Installing / Compile from source

To use FastRTP, you can clone the repository and build the plugin using Gradle.

```shell
git clone https://github.com/WinSMP/FastRTP.git
cd FastRTP/
./gradlew build
```

This will create a `FastRTP.jar` file in the `build/libs` directory, which you can then use to update your server.

### Building

To build the plugin, you will need to have Gradle installed on your system. Once you have cloned the repository, you can build the plugin using the following command:

```shell
./gradlew build
```

This will create a `FastRTP.jar` file in the `build/libs` directory.

## Features

FastRTP provides the following features:

* Random teleportation to safe locations within a specified range
* Configurable minimum and maximum range
* Configurable maximum attempts to find a safe location
* Preloads safe locations for faster teleportation
* Support for multiple worlds
* Folia support

## Configuration

FastRTP can be configured using the following options:

| Value | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `min-range` | Integer | 3000 | The minimum distance from (0,0) for a random teleport. |
| `max-pool-size` | Integer | 100 | The maximum number of pre-generated locations to keep in memory for fast teleports. |
| `max-pool-multiplier` | Integer | 5 | When the server is busy, the pool size is multiplied by this value. For example, with 10 players online, the pool size will be `10 * 5 = 50`. |
| `samples-per-chunk` | Integer | 8 | The number of random locations to check within a chunk. Higher values might find more valid spots but will use more CPU. |

### Preloader

These options are under the `preloader` key in the config file.

| Value | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `max-range` | Double | -1.0 | The maximum distance from (0,0) for a random teleport. If this value is 0 or less, it will be half the world border's diameter. This is always capped by the world border. |
| `max-attempts` | Integer | 50 | The maximum number of times to try finding a safe location before giving up. |
| `max-chunk-attempts` | Integer | 10 | The maximum number of chunks to check for a safe location. |
| `preload-interval-hours` | Integer | 1 | How often (in hours) the plugin should search for new locations to add to the pool. |
| `locations-per-hour` | Integer | 5 | The number of new locations to find and add to the pool during each pre-loading cycle. |

## Contributing

If you'd like to contribute to FastRTP, please fork the repository and use a feature branch. Pull requests are warmly welcome. Please see the `CONTRIBUTING.md` file for more information on how to contribute.

## Links

* Repository: <https://github.com/WinSMP/FastRTP/>
* Issue tracker: <https://github.com/WinSMP/FastRTP/issues>

## Licensing

The code in this project is licensed under the MPL-2.0 license. You can find the text version of the license in the `LICENSE` file.
