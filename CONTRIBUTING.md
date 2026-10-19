# Contributing

Thank you for helping. Three kinds of contribution are especially useful.

## Report a TV

Open an issue with:

- the TV or box model and its Android / Google TV version (Settings › System › About),
- what worked and what did not,
- a photo or `adb exec-out screencap -p > home.png` screenshot if something looks wrong.

## Add a list of apps to switch off

1. Create `examples/<brand-model>/debloat.txt`, one package per line, grouped with `#` comments that
   say what each package does. Copy the format of an existing folder.
2. Only list packages you have removed yourself and lived with for a few days.
3. Add a row to the table in `examples/README.md`.

Never include the Live TV app, HDMI/CEC services, remote or keyboard services, Google Play Services,
the Play Store or Bluetooth.

## Change the launcher

- Build with `cd launcher && ./gradlew assembleDebug` (JDK 17).
- Keep the house rules: animations only touch transform and alpha, no live blur, no new runtime
  permissions, every string in `values`, `values-vi` and `values-zh-rCN`.
- Describe what you changed and how you tested it on a real TV.

By contributing you agree that your work is released under the [MIT licence](LICENSE).
