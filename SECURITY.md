# Security

MoonCast is an early preview intended for a trusted local network. It has not received a security audit. Only the latest preview is maintained on a best-effort basis; there is no security response SLA.

## Report privately

Use this repository's **Security → Report a vulnerability** page when available: [private report](https://github.com/meta-tabchen/MoonCast/security/advisories/new). If GitHub does not offer that option, use the maintainer's [profile contact options](https://github.com/meta-tabchen) to arrange a private channel before sharing sensitive details. Do not post exploit instructions or private phone data in public issues.

Include the affected version, Android/ROM, prerequisites, impact, and minimal reproduction. Redact credentials, app-private files, and network identifiers.

## Permission model

Ordinary capture uses explicit MediaProjection consent. Playback capture requires Android permission and a source app that allows it. Input defaults off; Accessibility must be enabled by the user, and Root requires `su` authorization. The Root broker checks local peer UIDs and uses a random socket name. These mechanisms explain the implementation, not an assurance of security.

Pairing certificates, private keys, and client identities stay in private app storage and are excluded from backup. Do not expose host ports directly to the Internet or include these files in reports. Source builds and release packaging must exclude local credentials and signing keys.
