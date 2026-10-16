# Security Policy

Please report vulnerabilities privately to **security@stampmitra.in**
(see https://developer.stampmitra.in/.well-known/security.txt). Do not open
public issues for security problems. We aim to acknowledge reports within
3 business days.

Never include live API keys (`sk_live_...`) or personal data in a report.

## Supported versions

Pre-1.0: only the latest 0.x release receives fixes.

## Using the SDK safely

- Server-side only. Never embed `sk_` keys in Android apps, desktop installers or public repos.
- Load keys from the environment or a secrets manager.
- The SDK never follows redirects, never logs by default and redacts credentials and personal data when logging is on,
  but treat logs as sensitive anyway.
- Verify webhook signatures over the raw request body (`Webhook.constructEvent`).
