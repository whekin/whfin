# Personal release signing

WHFIN dogfood releases use one persistent private signing identity. Android accepts an in-place update
only when its application ID and signing certificate match the installed build, so losing or replacing
this key requires uninstalling the app before another release can be installed.

The repository never contains the key or its passwords. By default Gradle reads:

```text
~/.config/whfin/signing/release.properties
```

The location may be overridden with the `whfinSigningProperties` Gradle property. The file contains:

```properties
storeFile=/absolute/path/to/whfin-release.p12
storePassword=local-secret
keyAlias=whfin-release
keyPassword=local-secret
```

`./gradlew :app:assembleRelease` fails before packaging when this file, a required property, or the
keystore is missing. A successful build produces `app/build/outputs/apk/release/app-release.apk`; verify
its certificate before installation:

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

Keep an encrypted backup of both the PKCS12 keystore and properties outside the development machine.
They are the update identity for all installations made before Google Play owns release signing. Never
commit either file, copy their values into CI logs, or replace the key for an existing application ID.

The current personal-release certificate has SHA-256 fingerprint:

```text
af600982905fc962f57c0507f7042c5dbfe23ada4ca677c55495ccd6752fae92
```

Verify this fingerprint after installation. Before publishing each subsequent APK, increment
`versionCode` and update `versionName` in `app/build.gradle.kts`; do not reuse or decrease an installed
release version code.

For the first debug-to-release transition, export a portable WHFIN backup if the debug installation has
valuable data, uninstall the debug package, install the signed release, and restore the backup. Subsequent
release builds install in place and retain Room/DataStore data as long as `versionCode` only increases.
