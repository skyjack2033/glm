# Repository Presentation and Automation Design

Date: 2026-07-13

Status: Approved

## Purpose

This document defines how Wireless ME Hatch is presented to users and how GitHub Actions validate and publish the mod. The design follows established GTNH community repository patterns while keeping the workflow surface appropriate for a small, focused addon.

## Goals

- Provide equivalent Chinese and English project documentation.
- Make installation, binding, compatibility, configuration, and build requirements easy to find.
- Run the same CI policy on every temporary development branch and on pull requests to `master`.
- Publish a moving `dev-build` prerelease from successful `master` builds.
- Publish an immutable production release from `v*` tags.
- Upload only the production JAR to GitHub Releases.
- Keep only `master` as a long-lived branch.
- Remove starter-template workflows and internal development artifacts from the public repository.

## Non-Goals

- Publishing to Maven, Modrinth, or CurseForge.
- Maintaining a project website or wiki.
- Adding decorative branding without a real project asset.
- Changing mod behavior, recipes, configuration semantics, or runtime dependencies.
- Advertising compatibility that has not been tested.

## Community References

The design was informed by these repositories and their current default branches:

- [Twist Space Technology](https://github.com/Nxer/Twist-Space-Technology-Mod) at `main`.
- [GT: Not Leisure](https://github.com/ABKQPO/GT-Not-Leisure) at `dev-290`.
- [GTNH Actions Workflows](https://github.com/GTNewHorizons/GTNH-Actions-Workflows) at commit `219d11877bb9008ac107d7d11e01bccef5ecbb8c`.

The README structure takes TST's compact community-mod positioning and GTNL's clearer user-to-contributor flow. The CI wrapper follows the GTNH shared workflow, while release workflows remain local so they can enforce production-JAR-only publishing.

## Repository Documentation

### Language Structure

- `README.md` is the primary Simplified Chinese document.
- `README.en.md` is the English mirror.
- Each file links to the other before the title.
- Headings, tables, compatibility claims, commands, and configuration keys remain equivalent in both files.

### README Header

Use a centered text header with:

- Project name: `Wireless ME Hatch`.
- Literal positioning: a focused, unofficial GTNH community addon for wireless ME multiblock output.
- Three badges: CI status, latest release, and MIT license.

Do not add a generated logo, decorative banner, Discord/QQ badge, contributor wall, or unsupported download claim. A future real in-game screenshot can be added when one is deliberately captured and maintained.

### README Information Order

Both README files use this order:

1. Language link and project header.
2. Unofficial community-addon notice.
3. Purpose and feature summary.
4. Functional limits and connection-state behavior.
5. Tested compatibility and required mods.
6. Installation and downloads.
7. Wireless Link Tool binding and unbinding workflow.
8. Assembly status icons.
9. Configuration table.
10. Contributor build and verification commands.
11. Technical architecture links.
12. Contribution workflow.
13. License and community-project acknowledgements.

The README must distinguish the build JVM from the game runtime:

- Gradle 9.4 and `gtnhgradle` 2.0.26 require JDK 25 for builds.
- `runClient17` launches the Minecraft development client on Java 17.
- Jabel compiles the mod to Java 8 bytecode for Minecraft 1.7.10 compatibility.

### Supported Claims

The compatibility section may state the versions currently pinned and tested by the repository:

- Minecraft 1.7.10.
- Forge 10.13.4.1614.
- GTNH 2.9.0 beta test environment.
- GT5-Unofficial 5.09.52.594.
- Applied Energistics 2 rv3-beta-977-GTNH.

It must not imply support for older GTNH versions or a stable public release until such a release exists.

## Repository Cleanup

Remove these tracked development artifacts:

- `.wireless_temp`.
- `.zcode/`.
- `docs/superpowers/`.

Preserve `docs/architecture/` as the public technical documentation set.

Add root-anchored ignore rules for local agent artifacts so they cannot be recommitted accidentally:

- `/.superpowers/`.
- `/.zcode/`.
- `/.wireless_temp`.
- `/docs/superpowers/`.

## GitHub Actions Architecture

### CI: `.github/workflows/build-and-test.yml`

Triggers:

- Every branch push.
- Pull requests targeting `master`.
- Manual dispatch.

Security and execution policy:

- Workflow permission is `contents: read`.
- No custom secrets are inherited by the remote reusable workflow.
- Concurrency is grouped by pull request or branch ref, with older runs cancelled.
- The reusable workflow is pinned to GTNH Actions Workflows commit `219d11877bb9008ac107d7d11e01bccef5ecbb8c`.

The shared workflow supplies the GTNH build matrix, Gradle wrapper validation, caching, assembly, full build, server smoke test, reports, and CI artifacts.

### Development Release: `.github/workflows/dev-build.yml`

Triggers:

- Pushes to `master`.
- Manual dispatch for recovery.

Policy:

- Permission is limited to `contents: write`.
- Concurrency group is fixed to `dev-build`, with older runs cancelled.
- The job checks out the exact commit, installs JDK 25, configures Gradle, and runs `spotlessJavaCheck test build`.
- The production JAR selector excludes `*-dev.jar`, `*-sources.jar`, and `*-api.jar`, and fails unless exactly one production JAR exists.
- Only after a successful build, the workflow recreates the `dev-build` prerelease at the tested `master` commit.
- The release is marked prerelease and is not marked as the latest stable release.
- The uploaded file is the production JAR only.

The workflow uses the runner's GitHub CLI with `GITHUB_TOKEN`; no external release secret is required.

### Tagged Release: `.github/workflows/release.yml`

Triggers:

- Pushes of tags matching `v*`.

Policy:

- Permission is limited to `contents: write`.
- Release concurrency is grouped by tag and is never cancelled in progress.
- The job installs JDK 25 and reruns `spotlessJavaCheck test build` from the tagged commit.
- The production JAR selector uses the same exact-one rule as the development release.
- The workflow creates a normal GitHub Release for the existing tag and generates release notes.
- Only the production JAR is attached.
- Release creation failures fail the workflow; publishing steps do not use `continue-on-error`.

### Dependency Updates: `.github/dependabot.yml`

Dependabot checks both ecosystems weekly:

- `github-actions` from `/`.
- `gradle` from `/`.

Updates target `master` and use a small open-pull-request limit to avoid maintenance noise.

### Removed Automation

Delete these redundant or invalid starter-template files:

- `.github/workflows/gradle.yml`.
- `.github/workflows/gradle-publish.yml`.
- `.github/workflows/release-packages.yml`.
- `.github/workflows/release-tags.yml`.
- `.github/scripts/update_version`.

The removed workflows either run the Java-25-only build tooling on Java 17, duplicate CI and release behavior, package starter-template archives instead of the mod, or target a non-existent Groovy build file.

### Action Pinning

Every local third-party Action reference is pinned to a full commit SHA and carries a version comment. Floating tags such as `@master`, `@latest`, or `@v5` are not permitted in local workflows. The GTNH reusable workflow is also pinned to a full commit SHA.

## Branch Policy

- `master` is the only long-lived branch.
- New features are developed on temporary branches.
- Every temporary branch receives CI on push.
- Changes merge through pull requests after CI succeeds.
- Temporary local and remote branches are deleted after merge.
- `dev-build` updates only from `master`; development branches never publish releases.

## Failure Behavior

- A CI failure blocks confidence in the branch but never publishes an artifact as a release.
- A failed development-release build leaves the previous `dev-build` release unchanged.
- A release-update failure after a successful build fails visibly and is safe to rerun; GitHub release updates are not treated as atomic.
- A failed tagged build does not create or modify a formal release.
- Zero or multiple production JAR candidates fail release jobs before GitHub state changes.
- Missing release permissions or GitHub CLI failures fail the publishing workflow visibly.

## Verification

Implementation is complete only when all of these checks pass:

1. `README.md` and `README.en.md` have matching sections, commands, versions, and configuration keys.
2. Workflow YAML parses and GitHub recognizes all three workflows after push.
3. No workflow contains floating Action references or `secrets: inherit`.
4. `./gradlew spotlessJavaCheck test build --rerun-tasks --console=plain` succeeds locally.
5. JUnit XML reports the expected tests with zero failures, errors, or skips.
6. The production JAR selection returns exactly one file.
7. Removed template and internal-development paths are absent from the Git tree.
8. `git diff --check` emits no output.
9. GitHub shows only the `master` branch after cleanup.
10. The master CI run succeeds and the `dev-build` prerelease contains only the production JAR.
