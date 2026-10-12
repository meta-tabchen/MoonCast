# Stable TV signing: provisioning status

## Current status

The TV release pipeline is prepared to reuse one externally provisioned signing
identity. A usable, permanently retained TV signing key has **not been confirmed**.
The current GitHub integration does not expose Actions secret, variable, or
environment management. It also cannot inspect secret metadata, so lack of access
must not be presented as proof that the repository contains no existing secrets.

Source checks and unsigned builds can continue. Publication of an installable,
stably signed APK remains blocked on secure initial provisioning. No replacement
debug key, per-run key, new storage account, or private repository is provisioned
by this document. The owner does not need to choose a local key-backup workflow.

See [the signing and packaging contract](tv-signing.md) for the existing guards.

## Selected custody direction

Use the owner's existing GitHub repository, `meta-tabchen/MoonCast`, for managed
encrypted signing secrets once an authorized provisioning route is available.
This keeps ordinary future releases independent of the owner's computer. A
protected `tv-release` environment is preferable, with access restricted to the
reviewed release branch; it needs explicit configuration and a corresponding
workflow change before environment-only secrets will work.

GitHub encrypts secrets before storage, then decrypts them for authorized jobs.
This is managed secret storage, not a non-exportable hardware signing service.
Repository writers can arrange for workflows to access repository secrets, so
repository access and release code must remain trusted. Environment protections
provide additional controls where supported. See [GitHub secrets](https://docs.github.com/en/actions/concepts/security/secrets)
and [secure use guidance](https://docs.github.com/en/actions/reference/security/secure-use).

GitHub should not be described as an independent disaster-recovery backup. Losing
the only retained key can prevent future updates under the same app identity;
Android cannot regenerate that identity. See [Android app signing](https://developer.android.com/studio/publish/app-signing).

## Why a normal workflow cannot bootstrap this

The available `GITHUB_TOKEN` permissions contain neither repository Secrets nor
Variables nor environment-administration permission. Increasing `contents` or
`actions` permission does not add these capabilities. A fresh hosted runner is
also not permanent key storage. Therefore, generating a key in a normal build
would not solve persistent custody. Keys must never be put in source commits,
release assets, build artifacts, caches, or logs to work around this limitation.

Sources: [workflow permissions](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#permissions),
[hosted runners](https://docs.github.com/en/actions/reference/runners/github-hosted-runners).

## Minimum remaining secure provisioning

One initial provisioning handoff must establish signing authority and store the
credentials directly at GitHub. This is separate from permission to edit source
or run CI; there is no currently available one-click provisioning operation.
Do not request secret values in chat, read an existing credential out of another
integration, or create a new access token merely to bypass missing access.

Before provisioning:

1. Use an authorized secret-management session to inspect **names and metadata
   only** for an existing TV signing configuration. Preserve a reusable identity
   if one is present; do not silently rotate it.
2. Obtain explicit approval for any new signing credential and any new or expanded
   persistent access. Private-key/password transmission requires a secure,
   user-controlled handoff. A generated script or workflow does not remove that
   requirement. Prefer an existing suitable session to another permanent token.
3. Confirm that GitHub is the recipient of the keystore and its passwords, solely
   for signing MoonCast TV releases. The current repository-level contract needs
   four secrets: `MOONCAST_TV_KEYSTORE_BASE64`, `MOONCAST_TV_STORE_PASSWORD`,
   `MOONCAST_TV_KEY_ALIAS`, and `MOONCAST_TV_KEY_PASSWORD`. Base64 is encoding, not
   encryption. Store the public certificate fingerprint separately as
   `MOONCAST_TV_SIGNING_CERT_SHA256`.
4. For the current repository-level contract, the provisioning credential needs
   repository `Secrets: write` and `Variables: write` access to this repository.
   The protected-environment alternative needs `Administration: write` to create
   or configure the environment, and `Environments: write` for its secrets and
   variables. These are API permission requirements, not a request to grant all
   of them. Scope any separately approved access to the chosen route and revoke
   temporary provisioning access afterward.
5. Verify successful secret-name provisioning and the intended public certificate
   pin, then sign and independently verify the APK through the existing pipeline.
   Only report stable signing after the persisted configuration has been reused
   successfully. APK installation/update behavior still needs device evidence.

Official permission references: [repository and environment secrets](https://docs.github.com/en/rest/actions/secrets),
[repository and environment variables](https://docs.github.com/en/rest/actions/variables),
[environment creation](https://docs.github.com/en/rest/deployments/environments#create-or-update-an-environment).

No key generation, credential extraction, secret upload, account creation, or
environment change has been performed as part of preparing this guide.
