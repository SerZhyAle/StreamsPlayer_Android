# Runtime notices

The phone and Wear APKs package `assets/THIRD-PARTY-NOTICES.txt`. The inventory covers the
resolved store release runtime (including the common dependencies used by noLegal). Test and
build tooling dependencies are excluded.

Regenerate at release after changing runtime dependencies; the Gradle inventory has no recorded fast time:

```powershell
./gradlew.bat -I tools/third-party-notices/runtime-inventory.gradle :phone:spRuntimeInventory :wear:spRuntimeInventory
python tools/third-party-notices/generate.py
```

The generator reads the resolved artifacts and their cached Maven POMs. It preserves license
names/URLs declared by publishers and deduplicates identical embedded license and notice texts,
including those inside AAR classes.jar files. It does not assign an inferred license when a POM
omits one. Google Play services' own SDK license declarations remain separate from their embedded
third-party notices. The assets are versioned snapshots; the scratch inventories are not committed.
