# Releasing

Releases are cut by pushing a `vX.Y.Z` tag. The `Release` workflow builds, tests, signs and uploads the artifacts to
**Maven Central through the Sonatype Central Portal**. It is **inert until the repository variable `MAVEN_PUBLISH` is `true`**, and no
credential is stored in the repository: the secrets below live in a protected GitHub *environment*.

> Maven Central has no OIDC trusted publishing: a Portal user token and a GPG signing key are required. Keep them in the
> `maven-central` environment (required reviewers on), not in repository-level secrets.

## One-time setup (maintainer)

1. **Verify the namespace `in.stampmitra` first.** Sign in at <https://central.sonatype.com> with the organisation account, open
   *Namespaces*, add `in.stampmitra`, and prove ownership of the matching domain **`stampmitra.in`** by publishing the
   verification key the Portal shows as a **DNS TXT record** on `stampmitra.in`. Nothing can be published until the Portal shows the
   namespace as *Verified*. (The groupId `in.stampmitra` is the reversed domain `stampmitra.in`.)
2. **Portal token:** in the Portal, *Account > Generate User Token*. Store the two values as the secrets `CENTRAL_USERNAME` and
   `CENTRAL_PASSWORD`.
3. **GPG key:** `gpg --full-generate-key` (RSA 4096 or Ed25519), then publish the public key so Central can verify signatures:
   `gpg --keyserver keyserver.ubuntu.com --send-keys <KEYID>` (also `keys.openpgp.org`). Export the private key with
   `gpg --armor --export-secret-keys <KEYID>` into the secret `MAVEN_GPG_PRIVATE_KEY`, and its passphrase into
   `MAVEN_GPG_PASSPHRASE`.
4. **GitHub:** create the environment `maven-central` (Settings > Environments), add the four secrets to it and require a reviewer. Add
   the repository variable `MAVEN_PUBLISH` = `true` (Settings > Secrets and variables > Actions > Variables) when you are ready for
   the workflow to run. Optionally protect the `v*` tag pattern (Settings > Rules).

The pom already carries the metadata Central requires (name, description, url, licenses, developers, scm) and the `release` profile
attaches the sources jar and the javadoc jar, signs everything (`maven-gpg-plugin`) and uploads with
`central-publishing-maven-plugin` (`publishingServerId` `central`, `autoPublish=false`).

## Per release

1. Move the *Unreleased* notes into a new section in `CHANGELOG.md`.
2. Set the new version in `pom.xml` (`<version>`), `StampMitra.VERSION` (`src/main/java/in/stampmitra/sdk/StampMitra.java`) and
   `examples/pom.xml` (`stampmitra.version`); the workflow fails if the tag, the pom and `StampMitra.VERSION` disagree.
3. `mvn -B verify` locally (and optionally `mvn -B -Prelease verify -Dgpg.skip` to check the sources and javadoc jars).
4. `git tag vX.Y.Z && git push origin vX.Y.Z`.
5. The workflow runs the full build and the conformance suite, then uploads the signed bundle. The deployment is *validated* and then
   waits in the Central Portal (<https://central.sonatype.com/publishing/deployments>): review it and click **Publish**. Set
   `autoPublish` to `true` in the pom once you trust the pipeline. **Published versions are immutable.**
6. The workflow then creates the GitHub release with generated notes.

Until the setup is done and the variable is set, pushing a tag does nothing.
