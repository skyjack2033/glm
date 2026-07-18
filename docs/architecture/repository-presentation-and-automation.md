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
7. GTNH AE2 Wireless Kit Simple/Advanced/Super binding and unbinding workflow.
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

Remove these internal development artifacts:

- `.wireless_temp`.
- `.zcode/`.
- `docs/superpowers/`.

Remove these unrelated or invalid starter-template residues:

- `docs/FAQ.md`.
- `docs/migration.md`.
- `docs/porting.md`.
- `CODEOWNERS`.
- `jitpack.yml`.

The three documents are ExampleMod template guidance rather than project documentation, while the ownership and JitPack files are inactive starter metadata for services this repository does not use.

Preserve `docs/architecture/` as the public technical documentation set.

Add root-anchored ignore rules for local agent artifacts so they cannot be recommitted accidentally:

- `/.superpowers/`.
- `/.zcode/`.
- `/.wireless_temp`.
- `/docs/superpowers/`.

## Repository Metadata

- `src/main/resources/mcmod.info` links to `https://github.com/skyjack2033/glm`.
- The GitHub repository description will be changed from the ExampleMod starter text to `Wireless item and fluid output integration for GregTech New Horizons multiblocks.`.

## GitHub Actions Architecture

### CI: `.github/workflows/build-and-test.yml`

Triggers:

- Pushes to every branch. The explicit `branches: ['**']` filter means tag pushes are not included.
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
- The job checks out the exact commit, installs JDK 25, configures Gradle, and runs the full `clean spotlessJavaCheck test build --rerun-tasks --console=plain` build.
- The production JAR selector excludes `*-dev.jar`, `*-sources.jar`, and `*-api.jar`, and fails unless exactly one production JAR exists.
- `ARTIFACT_PATH` is passed from the selector through the publish step's environment and must resolve beneath `build/libs/`.
- A missing release is recognized only from an HTTP 404; all other API errors fail visibly instead of being treated as absence.
- On first publication, or when recovering a draft left by a failed attempt, the workflow creates a draft with the production JAR attached and publishes it only as the last step. The result is a prerelease with `latest=false`.
- When a published `dev-build` release already exists, the workflow first uploads one uniquely named staged asset. Only after that succeeds does it move the `dev-build` tag, update the release as a prerelease with `latest=false`, delete the old assets, and PATCH the staged asset back to the production filename.
- A final API check requires a published release containing exactly one asset with the production JAR filename.
- The uploaded file is the production JAR only; development, sources, and API JARs are never release assets.

The workflow uses the runner's GitHub CLI with `GITHUB_TOKEN`; no external release secret is required.

### Tagged Release: `.github/workflows/release.yml`

Triggers:

- Pushes of tags matching `v*`.

Policy:

- Permission is limited to `contents: write`.
- Release concurrency is grouped by tag and is never cancelled in progress.
- The job checks out the tagged commit, installs JDK 25, and runs the full `clean spotlessJavaCheck test build --rerun-tasks --console=plain` build.
- The production JAR selector uses the same exact-one rule as the development release.
- `ARTIFACT_PATH` is passed through the publish step's environment and must identify the selected production JAR beneath `build/libs/`.
- A draft left by a failed attempt is recycled so a rerun can converge; an already published or prerelease release for the tag is never overwritten.
- The workflow creates a draft for the existing tag with the production JAR attached, then publishes it as the final step and marks it as the latest release.
- Only the production JAR is attached; development, sources, and API JARs are excluded.
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

Every local third-party Action reference is pinned to a full commit SHA and carries a version comment:

- `actions/checkout` v5.0.1: `93cb6efe18208431cddfb8368fd83d5badbf9bfd`.
- `actions/setup-java` v5.5.0: `0f481fcb613427c0f801b606911222b5b6f3083a`.
- `gradle/actions/setup-gradle` v5.0.2: `0723195856401067f7a2779048b490ace7a47d7c`.

Floating tags such as `@master`, `@latest`, or `@v5` are not permitted in local workflows. The GTNH reusable workflow remains pinned to commit `219d11877bb9008ac107d7d11e01bccef5ecbb8c`.

## Branch Policy

- `master` is the only long-lived branch.
- New features are developed on temporary branches.
- Every temporary branch receives CI on push.
- Changes merge through pull requests after CI succeeds.
- Temporary local and remote branches are deleted after merge.
- `dev-build` updates only from `master`; development branches never publish releases.

## Failure Behavior

- A CI failure blocks confidence in the branch but never publishes an artifact as a release.
- A failed development or tagged build leaves its release state unchanged.
- When updating an existing published `dev-build`, an interrupted publication leaves either the previous production asset or the uniquely named staged asset downloadable. A rerun converges the release back to one normally named production JAR. A failed first publication may leave no release or a recyclable draft.
- A failed formal publication may leave no release or a recyclable draft; once a formal release is published, the workflow treats it as immutable and refuses to overwrite it.
- Zero or multiple production JAR candidates fail release jobs before any GitHub release state changes.
- HTTP and permission failures other than an expected not-found response remain visible and fail the publishing workflow.

## Verification

Implementation is complete only when all of these checks pass:

1. `README.md` and `README.en.md` have matching sections, commands, versions, and configuration keys.
2. `./gradlew clean spotlessJavaCheck test build --rerun-tasks --console=plain` succeeds locally.
3. `actionlint .github/workflows/*.yml` accepts all local workflow files.
4. Every local `uses:` reference has a full commit SHA, the shared workflow uses its recorded full SHA, and no workflow contains `secrets: inherit`.
5. The production JAR selector returns exactly one file after excluding development, sources, and API JARs.
6. Removed template and internal-development paths are absent from the Git tree, all four root-anchored ignore rules match, and the three `docs/architecture/` files remain tracked.
7. `git diff --check` emits no output.
