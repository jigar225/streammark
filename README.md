# StreamMark

**Streaming markdown for AI chat on Android Compose.**

StreamMark is a Jetpack Compose library that renders markdown while tokens stream in — built for AI chat UIs. It supports LaTeX, GFM tables and strikethrough, styled HTML boxes, images, and incremental parsing without waiting for the full response.

| | |
|---|---|
| **Package** | `io.edutor.streammark` |
| **Min SDK** | 24 |
| **Compile SDK** | 36 |
| **License** | [Apache 2.0](LICENSE) |

## Features

- Incremental parsing while `isStreaming = true`
- Inline and block LaTeX (JLatexMath)
- GFM tables and strikethrough (CommonMark)
- HTML boxes with inline CSS
- Images via Coil
- Custom `segmentRenderer` for app-specific segments (mindmap, charts, selection actions)
- Legacy Android `TextView` path (`MarkdownTextRenderer`, `MarkdownStyle`)

## Requirements

Your app must already use **Jetpack Compose** and a **Compose BOM** aligned with your project. StreamMark does not pull in Material3 or Activity Compose — add those in your app as usual.

## Installation

### Option A — Git submodule / local module (recommended until Maven is live)

1. Add this repo as a submodule or copy the folder into your project.
2. In `settings.gradle.kts`:

```kotlin
include(":streammark")
```

3. In your app `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":streammark"))
}
```

4. Enable Compose in the app module if not already enabled.

### Option B — Maven (after you publish)

Coordinates (configured in `build.gradle.kts`):

```kotlin
implementation("io.edutor:streammark:1.0.0")
```

Publish locally to test:

```bash
./gradlew :publishReleasePublicationToMavenLocal
```

Then add `mavenLocal()` to your app's repositories.

For **GitHub Packages** or **Maven Central**, configure a `publishing.repositories` block in `build.gradle.kts` with your credentials and repository URL.

## Quick start

```kotlin
import io.edutor.streammark.api.StreamMark
import io.edutor.streammark.api.StreamMarkConfig

@Composable
fun ChatMessage(text: String, stillStreaming: Boolean) {
    StreamMark(
        markdown = text,
        isStreaming = stillStreaming,
        config = StreamMarkConfig.Default,
    )
}
```

## Streaming

Pass `isStreaming = true` while the model is still generating. The parser reuses partial state so formatting stabilizes as new tokens arrive. Set `isStreaming = false` when the message is complete.

## Configuration

Use [`StreamMarkConfig`](src/main/java/io/edutor/streammark/api/StreamMarkConfig.kt) for sizes, colors, table styling, quote/hr appearance, and spacing.

```kotlin
StreamMark(
    markdown = text,
    isStreaming = false,
    config = StreamMarkConfig.Default.copy(plainTextSize = 16f),
)
```

`MarkdownRenderConfig` is a deprecated typealias — prefer `StreamMarkConfig` in new code.

## Custom segments

The default renderer covers text, LaTeX, tables, HTML boxes, and images. For mindmap/chart/mermaid blocks (or custom selection UI), provide `segmentRenderer`:

```kotlin
StreamMark(
    markdown = content,
    isStreaming = isStreaming,
    config = StreamMarkConfig.Default,
    segmentRenderer = { scope ->
        when (val segment = scope.segment) {
            is MessageContentSegment.VisualContentSegment -> {
                MyMindmapCard(segment, scope.modifier)
            }
            else -> ComposeMessageSegmentRenderer(
                segment = scope.segment,
                modifier = scope.modifier,
                enableLinks = scope.enableLinks,
                config = scope.config,
            )
        }
    },
)
```

Import `ComposeMessageSegmentRenderer` from `io.edutor.streammark.compose`.

## Public API

| API | Purpose |
|-----|---------|
| `StreamMark` | Main `@Composable` entry |
| `StreamMarkConfig` | Typography and colors |
| `StreamMarkSegmentScope` | Passed to custom `segmentRenderer` |
| `MessageContentSegment` | Parsed segment types |
| `MarkdownMapper` | Selection → raw markdown (Indic-script safe) |
| `EditableMarkdownRenderer` | Editable markdown field |
| `MarkdownStyle` / `MarkdownTextRenderer` | Legacy View rendering |

Internal packages (`parser`, `extraction`, `latex`, `spans`) are not guaranteed stable yet.

## Known limitations (v1.0.0)

- **`MarkdownHelper`** — stub only (plain text). Avoid for production rich text until replaced.
- **`VisualContentSegment`** — emitted by the parser; default renderer skips it. Use `segmentRenderer`.
- **ProGuard** — `consumer-rules.pro` is empty. Edutor ships without minify today; add keep rules if release shrinking breaks JLatexMath/CommonMark.

## Dependencies (transitive)

StreamMark bundles:

- AndroidX Core, AppCompat
- Compose UI (via BOM in your app)
- [CommonMark](https://github.com/commonmark/commonmark-java) + GFM tables & strikethrough
- [JLatexMath Android](https://github.com/noties/jlatexmath-android)
- [Coil](https://coil-kt.github.io/coil/) Compose

## Building

```bash
./gradlew assembleRelease
./gradlew publishReleasePublicationToMavenLocal   # optional
```

## Project layout

```
src/main/java/io/edutor/streammark/
├── api/       # StreamMark, StreamMarkConfig, segments
├── parser/    # HybridStreamingParser, MarkdownMapper
├── compose/   # Default segment renderers
├── extraction/
├── latex/
├── spans/
└── ui/        # Legacy View path
```

## Contributing

We welcome fixes and improvements. **Discuss larger changes before opening a PR** — see [CONTRIBUTING.md](CONTRIBUTING.md).

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

Apache License 2.0 — see [LICENSE](LICENSE).

Copyright © 2026 **Edutor**, **Jigar Joshi**, and **Vishal Trivedi**.
