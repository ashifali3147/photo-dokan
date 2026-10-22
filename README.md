# Photo Dokan

Free, open-source software for small photo studios that print passport and stamp-size photos.
Take a photo on a phone or import it on the PC, make small adjustments, choose the size and
number of copies, and print at the exact physical size on A4 photo paper.

- **Two apps, one codebase:** Android and Windows, built with Kotlin Multiplatform and Compose Multiplatform.
- **No wasted paper:** customers are packed onto a 5 × 8 grid, and a part-used sheet goes back in the printer.
- **Exact size:** all layout is done in millimetres and calibrated per printer.
- **Offline and private:** no account, no cloud. Customer photos never leave the studio.

> Status: early development. The shared core (sheet packing, geometry, edit model, photo library
> paths) is in place with tests. Editing, printing and the phone-to-PC Print Station come next.

## Project structure

| Module | What it holds |
| --- | --- |
| `shared/core` | Pure Kotlin: domain model, mm geometry, packing engine, library layout. No platform code. |
| `shared/ui` | Compose Multiplatform screens shared by both apps. |
| `app-desktop` | Windows app (runs on macOS/Linux for development). |
| `app-android` | Android app. |

Imaging (OpenCV, ONNX Runtime), PDF and printing modules are added in later phases.

## Requirements

- Android Studio (latest stable) with the Kotlin Multiplatform plugin
- JDK 17 or newer
- Android SDK (compile SDK 36)

## Run

```bash
./gradlew :shared:core:desktopTest      # core unit tests
./gradlew :app-desktop:run              # desktop app
./gradlew :app-android:installDebug     # Android app on a connected device
./gradlew :app-desktop:packageMsi       # Windows installer (run on Windows)
```

CI builds and tests every push. The Windows MSI is built on tags (`v*`) or from the
**Actions → CI → Run workflow** button.

## Licence

MIT. See [LICENSE](LICENSE).
