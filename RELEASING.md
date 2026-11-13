# Releasing

Releases are a Maven repository on GitHub Pages: the `gh-pages` branch, its `maven/` directory, served at
https://okay-effects.github.io/okay-kernel/maven. No account and no token is needed to use it, only a repository.

Released: the API alone, `io.github.okay-effects:okay-kernel-api` (pure Java, Java 21 bytecode, no dependency) —
what okay-java depends on. The other modules are built and published locally (`dev.okay`, the SNAPSHOT).

## Using a release

Gradle:

```kotlin
repositories {
    mavenCentral()
    maven("https://okay-effects.github.io/okay-kernel/maven")
}

dependencies {
    implementation("io.github.okay-effects:okay-kernel-api:0.1.0")
}
```

sbt:

```scala-sbt
resolvers += "okay-kernel" at "https://okay-effects.github.io/okay-kernel/maven"

libraryDependencies += "io.github.okay-effects" % "okay-kernel-api" % "0.1.0"
```

## Making one

1. On `master`, everything green: `sbt api/test`.

2. The repository written into `target/pages-maven/`. The release's coordinates are given on the command line
   (`-Drelease.api`, `build.sbt`): without it the module stays `dev.okay:okay-kernel-api:0.2.0-SNAPSHOT`, as the
   local builds that depend on it (okay-core) know it:

   ```sh
   rm -rf target/pages-maven
   sbt -Drelease.api=0.1.0 api/publish
   ```

3. Onto `gh-pages`, under `maven/`, beside the releases already there, and the tag:

   ```sh
   git worktree add /tmp/okay-kernel-pages gh-pages   # the first time: git worktree add --orphan -b gh-pages /tmp/okay-kernel-pages
   mkdir -p /tmp/okay-kernel-pages/maven
   rsync -a target/pages-maven/ /tmp/okay-kernel-pages/maven/
   touch /tmp/okay-kernel-pages/.nojekyll
   git -C /tmp/okay-kernel-pages add -A
   git -C /tmp/okay-kernel-pages commit -m "okay-kernel-api 0.1.0"
   git -C /tmp/okay-kernel-pages push origin gh-pages
   git tag okay-kernel-api-v0.1.0 && git push origin okay-kernel-api-v0.1.0
   ```

   The first time, GitHub Pages is turned on for the branch `gh-pages`.

A release is never rewritten: a version published is what every build that resolved it has.
