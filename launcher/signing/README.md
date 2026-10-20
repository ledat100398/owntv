# Signing key

`owntv.jks` signs every OwnTV build: the APK on the Releases page and the one `OwnTV.cmd` builds on
your own computer. Because they share one key, either can update the other without uninstalling.

The key and its password (`owntv-public`) are deliberately public. Android uses the signature only to
decide whether an APK may update the copy already installed; it does not make a build trustworthy.
Download OwnTV only from this repository's Releases page, or build it yourself from the source.
