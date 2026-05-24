# Contributing to StreamMark

Thank you for your interest in StreamMark. This project is licensed under [Apache License 2.0](LICENSE).

## Before you start

**Please talk to us before significant work.** Open a [GitHub Issue](https://github.com/streammark/issues) (or contact the maintainers) and describe:

- What you want to change or add
- Why it helps the library
- Rough approach (especially for parser/rendering changes)

Small fixes (typos, docs, obvious bugs) can go straight to a pull request. For anything that touches **parsing, rendering behavior, or public API**, get a quick 👍 from a maintainer first so we do not waste your time.

**Maintainers:** Edutor · Jigar Joshi · Vishal Trivedi

## What the license allows (Apache 2.0)

Anyone may **use**, **modify**, and **distribute** this code under the terms in [LICENSE](LICENSE), including in commercial apps. They must keep the copyright notice and license text.

Contributing to **this repository** is separate: we use the process below so changes match Edutor’s needs and stay stable.

## How to contribute

1. **Fork** the repo and create a branch from `main`  
   Example: `fix/table-border-spacing` or `docs/readme-install`

2. **Build** locally:
   ```bash
   ./gradlew assembleRelease
   ```

3. **Change scope**
   - Prefer small, focused PRs
   - Match existing Kotlin style and package layout
   - Do **not** change parsing/rendering logic unless fixing a bug or agreed in an issue
   - Chapter AI–specific UI belongs in the Edutor app, not in this library

4. **Commit message format**
   ```
   type: short summary (≤ 72 chars)

   Optional body: what and why. Link issue #123 if any.
   ```
   Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`

5. **Open a Pull Request**
   - Fill in the PR template (if present) or describe changes clearly
   - Note if behavior visible to apps changed
   - Ensure `./gradlew assembleRelease` passes

## Code guidelines

- Public API lives under `io.edutor.streammark.api`
- Prefer `StreamMarkConfig` over deprecated `MarkdownRenderConfig`
- No Edutor app–only dependencies in the library
- Avoid user-facing strings in the library for v1 unless discussed (or use `strings.xml` with a clear i18n plan)

## Questions

Open an issue with the **question** label or reach out to the maintainers listed above.

By submitting a pull request, you agree that your contribution is licensed under the same [Apache 2.0](LICENSE) license as the project.
