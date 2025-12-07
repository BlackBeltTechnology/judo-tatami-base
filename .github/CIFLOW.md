# Development version and branch handling

## Branches

Versioning policy of JUDO NG modules are based on GitFlow: https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow.

Branches:

- **develop**: development branch contains latest development sources of the last active version
- **feature/JNG-NUMBER_short_summary**: feature branches are based on **develop** and contains sources of new features that will be included in last active version
- **(release/)1_0_beta1**: release branches of 1.0-beta1 (release/ prefix is still reserved for CI)
- **bugfix/JNG-NUMBER_short_summary**, **support/JNG-NUMBER_short_summary**: bugfix and support branches are based on release branches and must be applied to release and development branches of newer versions too
- **master**: contains latest released sources of the last active version

```mermaid
gitGraph
    commit id: "initial"
    branch develop
    checkout develop
    commit id: "dev-1"
    branch feature/JNG-1
    checkout feature/JNG-1
    commit id: "feat-1"
    commit id: "feat-2"
    checkout develop
    merge feature/JNG-1
    branch feature/JNG-2
    checkout feature/JNG-2
    commit id: "feat-3"
    checkout develop
    merge feature/JNG-2
    branch release/1.0-beta1
    checkout release/1.0-beta1
    commit id: "rel-1"
    branch bugfix/JNG-4
    checkout bugfix/JNG-4
    commit id: "bugfix-1"
    checkout release/1.0-beta1
    merge bugfix/JNG-4
    checkout develop
    merge release/1.0-beta1
    checkout main
    merge release/1.0-beta1 tag: "v1.0-beta1"
```

## Version numbers

Version numbers are increased using semantic versioning:

- do not change version numbers on starting feature/ branches
- 2nd number in version of **develop** branch is increased when a release branch started
- do not change version numbers on bugfix/ branches - that are applied on release branches during testing before releasing it (merging to master)
- 3rd number in version of support/ branches is increased when started - it is used to support a previous release including new (minor) changes; support/ branches are merged back to release branch when update is released (without merging changes to master)
- 4th number in version of hotfix/ branches is increased when started (that are applied on both release and master branches)

## GitHub Action Flows

### build.yml

```mermaid
flowchart TD
    A[Push on develop OR<br/>PR on develop, master,<br/>increment/*, release/*] --> B{Commit or PR base branch?}
    B -->|master, release/*| C[Set version from pom.xml<br/>without -SNAPSHOT]
    B -->|develop, increment/*| D[Set version:<br/>major.minor.qualifier.date_commitId_branchName]
    C --> E[Build and deploy to nexus]
    D --> E
    E --> F[Create git tag v&lt;version&gt;]
    F --> G{PR or commit base branch?}
    G -->|increment/*, release/*| H[Create tag merge-pr/&lt;version&gt;]
    H --> I[Trigger merge-pr-tagged.yml]
    G -->|develop| J[Build change log]
    J --> K[Create GitHub release<br/>prerelease with change log]
```

### merge-pr-tagged.yml

```mermaid
flowchart TD
    A[Push on merge-pr/* tag] --> B[Get version from tag name]
    B --> C{Check version format}
    C -->|major.minor.qualifier| D[Merge PR to master]
    D --> E[Trigger create-release-on-master.yml]
    C -->|other| F[Squash PR to develop]
    F --> G[Trigger build.yml]
    D --> H[Delete tag merge-pr/&lt;version&gt;]
    F --> H
```

### create-release-on-master.yml

```mermaid
flowchart TD
    A[Push on master branch] --> B[Get version from tag name]
    B --> C[Build change log]
    C --> D[Create GitHub release<br/>last with change log]
```

### release.yml

```mermaid
flowchart TD
    A[Manual trigger with<br/>given version] --> B{given version is?}
    B -->|'auto'| C[Set release version from pom.xml<br/>without -SNAPSHOT]
    B -->|other| D[Set release version to<br/>given version]
    C --> E[Set next version to<br/>release version qualifier + 1]
    D --> E
    E --> F[Create PR on master<br/>with release version]
    F --> G[Trigger build.yml]
    E --> H[Create PR on develop<br/>with next version]
    H --> I[Trigger build.yml]
```

## How to develop

For issue tracking we are using [JIRA](https://blackbelt.atlassian.net/jira/dashboards). Golden rule:

> **IMPORTANT**: There is no commit without ticket number

So for pull request or commit `JNG-xxx` have to be presented in the commit.
