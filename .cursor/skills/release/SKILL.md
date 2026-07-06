---
name: release
description: >-
  Release the Armeria IntelliJ plugin to GitHub Releases. Use when asked to cut a
  release, bump pluginVersion, patch CHANGELOG, tag vX.Y.Z, or publish a plugin ZIP.
---

# Plugin release (GitHub Releases)

This repository distributes the plugin as a ZIP on [GitHub Releases](https://github.com/nise-nabe/armeria-intellij-plugin/releases). There is **no** automated release workflow on `main` today (the old `publishing.yml` was removed in 2025-07). Releases are prepared on a branch, merged to `main`, then tagged and published manually.

JetBrains Marketplace publishing (`publishPlugin` / `signPlugin`) is **not** configured in this repo.

## Version and changelog sources

| Item | Location |
|------|----------|
| Plugin version | `gradle.properties` → `pluginVersion` |
| Changelog | `plugin/CHANGELOG.md` |
| Plugin ID | `com.linecorp.intellij.armeria-intellij-plugin` (`plugin/build.gradle.kts`) |
| Built ZIP | `plugin/build/distributions/plugin-<version>.zip` |

Changelog Gradle settings live in `plugin/build.gradle.kts` and must stay aligned with Keep a Changelog style:

- `unreleasedTerm = "[Unreleased]"` — **keep square brackets**
- `header = "[${version}] - ${date()}"` — released headers use brackets too
- `groups` = `Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, `Security` — empty templates under `[Unreleased]` are recreated after `patchChangelog`

### `[Unreleased]` template (do not strip)

```markdown
## [Unreleased]
### Added

### Changed

### Deprecated

### Removed

### Fixed

### Security
```

After `patchChangelog`, verify brackets and all six subsection headers remain. If `patchChangelog` removes them, restore manually and fix the `changelog { }` block before retrying.

## Release checklist

### 1. Prepare the release branch

```bash
git checkout main
git pull origin main
git checkout -b cursor/release-<version>-<suffix>   # suffix assigned per agent session, e.g. 0716
```

### 2. Write release notes

Add entries under the appropriate `###` sections in `plugin/CHANGELOG.md` → `## [Unreleased]`.

### 3. Bump version and patch changelog

In `gradle.properties`, set a release version **without** `-SNAPSHOT`:

```properties
pluginVersion=0.2.0
```

Then patch the changelog (moves `[Unreleased]` content into a new version section and recreates the template):

Prefer Gradle MCP:

```json
{ "tasks": [":plugin:patchChangelog"], "arguments": ["-Prelease.version=0.2.0"] }
```

→ `gradle_run_tasks` (use `background: true` + poll if cold start)

Shell fallback: `./gradlew :plugin:patchChangelog -Prelease.version=0.2.0`

Review `plugin/CHANGELOG.md`:

- New header must be `## [0.2.0] - YYYY-MM-DD` (brackets required)
- `## [Unreleased]` must still list all six empty `###` sections

### 4. Verify

Prefer Gradle MCP (`gradle_run_tasks` with `background: true` + poll):

```json
{ "tasks": ["build", ":plugin:buildPlugin"], "background": true }
```

Shell fallback: `./gradlew build :plugin:buildPlugin`

Confirm the artifact exists:

```bash
ls plugin/build/distributions/plugin-<version>.zip
```

Optional sanity check — MCP:

```json
{ "tasks": [":plugin:getChangelog"], "arguments": ["--unreleased"] }
```

→ `gradle_run_tasks`

### 5. Merge to `main`

Commit only version/changelog files (not build outputs):

```bash
git add gradle.properties plugin/CHANGELOG.md
git commit -m "chore: release <version>"
git push -u origin cursor/release-<version>-<suffix>
```

Open a PR with **ManagePullRequest** (see `.cursor/skills/cloud-github/SKILL.md` and `.cursor/rules/pr-description-format.mdc`). Merge to `main` before tagging.

### 6. Tag on `main`

After merge:

```bash
git checkout main
git pull origin main
git tag -a v<version> -m "Release <version>"
git push origin v<version>
```

Tag the merge commit on `main`, not the feature-branch tip.

### 7. Publish GitHub Release

Rebuild on `main` so the ZIP matches the tagged commit (prefer MCP `gradle_run_tasks` `{ "tasks": [":plugin:buildPlugin"], "background": true }`; shell fallback below):

```bash
./gradlew :plugin:buildPlugin
cp plugin/build/distributions/plugin-<version>.zip /tmp/armeria-intellij-plugin-<version>.zip
```

Create the release with `gh` (after `gh auth status` succeeds — see `cloud-github` skill):

```bash
gh release create v<version> \
  /tmp/armeria-intellij-plugin-<version>.zip \
  --title "v<version>" \
  --prerelease \
  --notes "$(cat <<'EOF'
## Armeria IntelliJ Plugin <version>

### Added
- …

### Fixed
- …

**Full Changelog**: https://github.com/nise-nabe/armeria-intellij-plugin/compare/v<previous>...v<version>
EOF
)"
```

Conventions from past releases (`v0.0.1`–`v0.1.0`):

- Asset name: `armeria-intellij-plugin-<version>.zip` (not `plugin-<version>.zip`)
- Mark as **pre-release** unless explicitly shipping a stable GA
- Release notes summarize `plugin/CHANGELOG.md`; link the compare URL

### 8. Post-release (next development cycle)

Bump to the next snapshot in a follow-up PR:

```properties
pluginVersion=0.2.1-SNAPSHOT
```

Ensure `## [Unreleased]` still has the six empty section templates.

## Gradle tasks reference

| Task | Purpose |
|------|---------|
| `:plugin:patchChangelog` | Move `[Unreleased]` → `[version]`; recreate unreleased template |
| `:plugin:buildPlugin` | Produce distributable ZIP |
| `:plugin:build` / `build` | Full compile + test gate (run before release) |
| `:plugin:getChangelog` | Inspect parsed changelog (`--unreleased` for draft section) |
| `:plugin:publishPlugin` | Marketplace publish (**not set up**) |
| `:plugin:signPlugin` | Marketplace ZIP signing (**not set up**) |

## Cloud Agent notes

- Use branch prefix `cursor/` and session suffix (e.g. `cursor/release-0.2.0-0716`).
- Prefer **ManagePullRequest** for the version-bump PR; use `gh release create` only after `gh auth status` succeeds.
- Do not commit `plugin/build/` artifacts; upload the ZIP only to GitHub Releases.
- CI (`.github/workflows/main.yml`) runs `./gradlew build` on PRs — wait for green before merging the release PR.

## Historical context

| Era | Mechanism |
|-----|-----------|
| v0.0.1–v0.0.5 (2021–2022) | Tag push triggered `.github/workflows/publishing.yml` → `publish githubReleaseUpload` |
| 2025-07 onward | Workflows removed; manual GitHub Release |
| v0.1.0 (2026-07-04) | First release after workflow removal; established current manual flow |
