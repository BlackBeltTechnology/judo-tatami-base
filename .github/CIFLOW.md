# Development Version and Branch Handling

This document describes the Git branching strategy, versioning policy, and CI/CD automation workflows used by this project.

## Branches

The versioning and branching strategy follows [GitFlow](https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow).

| Branch Pattern | Base Branch | Purpose |
|----------------|-------------|---------|
| `develop` | — | Main development branch with the latest sources of the active version |
| `feature/JNG-NUMBER_short_summary` | `develop` | New features for the next release |
| `(release/)X.Y.Z` | `develop` | Stabilization branch for a specific release (the `release/` prefix is reserved for CI) |
| `bugfix/JNG-NUMBER_short_summary` | release branch | Bug fixes during release testing — must also be applied to newer release and develop branches |
| `support/JNG-NUMBER_short_summary` | release branch | Minor changes to a previous release; merged back to the release branch |
| `hotfix/JNG-NUMBER_short_summary` | `master` | Critical fixes applied to both release and master branches |
| `master` | — | Contains the latest released (stable) sources |

```mermaid
gitGraph
    commit id: "init"
    branch develop order: 1
    commit id: "dev-1"
    branch feature/JNG-1 order: 3
    commit id: "feat-1a"
    commit id: "feat-1b"
    checkout develop
    branch feature/JNG-2 order: 4
    commit id: "feat-2a"
    checkout develop
    merge feature/JNG-2 id: "merge feat-2"
    merge feature/JNG-1 id: "merge feat-1"
    branch release/1.0-beta1 order: 2
    commit id: "rc-1"
    branch bugfix/JNG-4 order: 5
    commit id: "fix-4"
    checkout release/1.0-beta1
    merge bugfix/JNG-4 id: "merge fix-4"
    checkout develop
    merge release/1.0-beta1 id: "release merge"
    checkout main
    merge release/1.0-beta1 id: "v1.0"
```

## Version Numbers

Versions follow semantic versioning with these rules:

| Event | Version Change | Example |
|-------|---------------|---------|
| Start a `feature/` branch | No change | Inherits from `develop` |
| Start a `release/` branch | Increment 2nd number on `develop` | `1.1.0-SNAPSHOT` → `1.2.0-SNAPSHOT` |
| Start a `bugfix/` branch | No change | Applied on release branch before merge to `master` |
| Start a `support/` branch | Increment 3rd number | `1.0.0` → `1.0.1` |
| Start a `hotfix/` branch | Increment 4th number | `1.0.0` → `1.0.0.1` |

### Development vs. Release Versions

- **Development builds** (from `develop` or `increment/*`): version is `major.minor.qualifier.timestamp_commitHash_branchName`
- **Release builds** (from `master` or `release/*`): version is the clean `major.minor.qualifier` from `pom.xml`

## GitHub Actions Workflows

The CI/CD pipeline is composed of four interconnected workflows:

```mermaid
flowchart TD
    subgraph "build.yml"
        trigger1["Push to develop\nor PR to develop/master/increment/release"]
        branch_check{Branch type?}
        version_dev["Version:\nmajor.minor.qual.timestamp_hash_branch"]
        version_rel["Version:\nmajor.minor.qualifier"]
        build["Build & deploy to Nexus"]
        tag["Create git tag\nv{version}"]
        merge_tag{"increment/* or\nrelease/*?"}
        create_merge_tag["Create tag\nmerge-pr/{version}"]
        changelog["Build changelog"]
        gh_release["Create GitHub release\n(prerelease)"]

        trigger1 --> branch_check
        branch_check -->|develop, increment/*| version_dev
        branch_check -->|master, release/*| version_rel
        version_dev --> build
        version_rel --> build
        build --> tag
        tag --> merge_tag
        merge_tag -->|Yes| create_merge_tag
        merge_tag -->|No| changelog
        create_merge_tag --> changelog
        changelog --> gh_release
    end

    subgraph "merge-pr-tagged.yml"
        trigger2["Push on merge-pr/* tag"]
        format_check{Version format?}
        merge_master["Merge PR to master"]
        squash_develop["Squash PR to develop"]
        delete_tag["Delete merge-pr tag"]

        trigger2 --> format_check
        format_check -->|major.minor.qualifier| merge_master
        format_check -->|other| squash_develop
        merge_master --> delete_tag
        squash_develop --> delete_tag
    end

    subgraph "create-release-on-master.yml"
        trigger3["Push to master"]
        final_changelog["Build changelog"]
        final_release["Create GitHub release\n(final)"]

        trigger3 --> final_changelog
        final_changelog --> final_release
    end

    subgraph "release.yml"
        trigger4["Manual trigger\nwith version"]
        auto_check{Version = 'auto'?}
        set_from_pom["Version from pom.xml\n(without -SNAPSHOT)"]
        set_given["Use given version"]
        calc_next["Next version =\nqualifier + 1"]
        pr_master["Create PR → master\nwith release version"]
        pr_develop["Create PR → develop\nwith next version"]

        trigger4 --> auto_check
        auto_check -->|Yes| set_from_pom
        auto_check -->|No| set_given
        set_from_pom --> calc_next
        set_given --> calc_next
        calc_next --> pr_master
        calc_next --> pr_develop
    end

    create_merge_tag -.->|triggers| trigger2
    merge_master -.->|triggers| trigger3
    pr_master -.->|triggers| trigger1
    pr_develop -.->|triggers| trigger1
```

### Workflow Details

#### `build.yml` — Main CI Pipeline

This is the primary build workflow triggered on every push to `develop` and on pull requests targeting `develop`, `master`, `increment/*`, or `release/*` branches.

**What it does:**
1. Calculates the version — development builds get a timestamped qualifier, release builds use the clean version from `pom.xml`
2. Builds the project and deploys artifacts to the JuDong Nexus repository
3. Creates a git tag `v{version}`
4. For `increment/*` and `release/*` branches, creates an additional `merge-pr/{version}` tag that triggers the merge workflow
5. For `develop` builds, generates a changelog and creates a GitHub prerelease

#### `merge-pr-tagged.yml` — PR Merge Handler

Triggered when a `merge-pr/*` tag is pushed. Routes the PR to the correct target:
- **Clean version** (`major.minor.qualifier`) → merge to `master` (triggers release on master)
- **Development version** → squash merge to `develop` (triggers a new develop build)

Cleans up the `merge-pr/*` tag after processing.

#### `create-release-on-master.yml` — Master Release

Triggered on push to `master`. Builds a changelog and creates a final (non-prerelease) GitHub release.

#### `release.yml` — Manual Release Trigger

Manually triggered with an optional version parameter:
- `auto` — reads the version from `pom.xml` (stripping `-SNAPSHOT`)
- Any `major.minor.qualifier` — uses the given version directly

Creates two pull requests:
1. PR to `master` with the release version
2. PR to `develop` with the next incremented version

## Development Guidelines

> **Important:** There is no commit without a JIRA ticket number. Every commit and PR must reference `JNG-xxx` in the message.

For issue tracking, use [JIRA](https://blackbelt.atlassian.net/jira/dashboards).
