# Hasteless Bedrock Breaker — Minecraft 26.3

A Fabric client mod that automates bedrock breaking without Haste II. This fork ports [Beta-51/hasteless-bedrock-breaker](https://github.com/Beta-51/hasteless-bedrock-breaker) to Minecraft 26.3 while preserving its task logic, commands and configuration.

[Deutsche Anleitung](README_DE.md)

## Install

Use Minecraft Java Edition **26.3**, **Java 25**, **Fabric Loader 0.19.3+**, and **Fabric API 0.161.0+26.3**. Put the mod JAR and Fabric API in your client instance's `mods` directory.

Build artifacts are available from this repository's [GitHub Actions](https://github.com/mcsnack/hasteless-bedrock-breaker/actions). Install the mod JAR, not the `-sources.jar`.

## Use

Toggle with `/bedrockMiner`, or right-click bedrock with an empty hand. Left-click bedrock to add a task. Existing behavior, region-task, debug and configuration commands are retained.

Use only where the server permits this mod. Server changes to piston/bedrock behavior can affect whether breaking succeeds.

## Build and validation

With JDK 25:

```sh
./gradlew build
```

The JAR is written to `build/libs/`. CI builds the mod and launches a software-rendered client with a mixin audit. The audit option is enabled only in development runs, not normal installed clients.

Gameplay still needs a separate test in a disposable world or authorized test server, especially bedrock breaking without Haste II, region tasks, material switching, range recovery and reconnecting.

## Attribution

Based on Beta-51's hasteless fork of [bunnyi116/fabric-bedrock-miner](https://github.com/bunnyi116/fabric-bedrock-miner), originally based on [LXYan2333/Fabric-Bedrock-Miner](https://github.com/LXYan2333/Fabric-Bedrock-Miner). Existing author credits and license files are retained.
