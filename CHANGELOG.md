# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-05-24

### Added

- Initial public release extracted from Edutor.
- `StreamMark` Compose entry point with streaming incremental parsing.
- LaTeX (inline and block), GFM tables and strikethrough, HTML boxes, images.
- `HybridStreamingParser`, default `ComposeMessageSegmentRenderer`, and `StreamMarkConfig`.
- `segmentRenderer` hook for custom per-segment UI (e.g. mindmap/chart cards).
- Legacy View path: `MarkdownStyle`, `MarkdownTextRenderer`, `EditableMarkdownRenderer`.
- Maven Publish configuration (`io.edutor:streammark:1.0.0`).

### Notes

- `MarkdownHelper` is a stub (plain text only) until a universal renderer ships.
- `VisualContentSegment` (mindmap/chart/mermaid) is parsed but not rendered by the default renderer — supply a custom `segmentRenderer`.
- `MarkdownRenderConfig` is a deprecated typealias for `StreamMarkConfig`.

[1.0.0]: #

