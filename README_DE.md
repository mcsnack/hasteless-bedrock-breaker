# Hasteless Bedrock Breaker für Minecraft 26.3

Client-Mod für Fabric, basierend auf Beta-51/hasteless-bedrock-breaker. Die bestehende Logik zum Entfernen von Bedrock ohne Haste II sowie die Befehle und Konfiguration bleiben erhalten.

## Voraussetzungen

- Minecraft Java Edition 26.3
- Java 25
- Fabric Loader 0.19.3 oder neuer
- Fabric API 0.161.0+26.3

Die Mod-JAR und Fabric API gehören in den `mods`-Ordner der 26.3-Client-Instanz.

## Bedienung

Mit `/bedrockMiner` aktivieren oder deaktivieren. Alternativ mit leerer Hand auf Bedrock rechtsklicken. Anschließend Bedrock mit Linksklick als Aufgabe hinzufügen. Bestehende Einstellungen und weitere Befehle bleiben verfügbar.

## Bauen

Mit JDK 25 im Projektverzeichnis:

```sh
./gradlew build
```

Windows:

```powershell
.\gradlew.bat build
```

Die installierbare JAR liegt in `build/libs/`; die Datei mit `-sources` ist für die Entwicklung. GitHub Actions stellt die Mod als Build-Artefakt bereit.

## Prüfung im Spiel

Vor produktiver Nutzung in einer separaten Testwelt prüfen: Clientstart, Befehle, Aktivieren/Deaktivieren, einzelne und regionale Aufgaben, Werkzeug-/Materialwechsel, Verlassen der Reichweite, Dimensionswechsel und erneutes Verbinden. Insbesondere muss der Bedrock-Abbau ohne Haste II auf der gewünschten Serverversion überprüft werden. Serverregeln und Änderungen am Bedrock-/Piston-Verhalten können den Erfolg beeinflussen.

## Herkunft

- [Beta-51/hasteless-bedrock-breaker](https://github.com/Beta-51/hasteless-bedrock-breaker)
- [bunnyi116/fabric-bedrock-miner](https://github.com/bunnyi116/fabric-bedrock-miner)

Urheberangaben und die vorhandene Lizenzdatei bleiben erhalten.
