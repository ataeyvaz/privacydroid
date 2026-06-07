# Contributing to PrivacyDroid

Thanks for your interest in improving PrivacyDroid! This project exists to make
mobile surveillance **visible** to ordinary users. Every contribution that serves
that goal — code, documentation, translations, bug reports — is welcome.

---

## Ground rules

PrivacyDroid has one non-negotiable principle: **no user data ever leaves the
device.** Any contribution that adds analytics, crash reporting, cloud sync, ads,
or a third-party tracking SDK will be rejected. If a change makes a network
request for any reason, it must be explained explicitly in the pull request.

---

## How to contribute

1. **Find or open an issue.** For anything non-trivial, open an issue first so we
   can agree on the approach before you write code.
2. **Fork** the repository and create a feature branch:
   `git checkout -b feature/short-description`.
3. **Make your change**, following the coding standards below.
4. **Build and test** locally:
   ```bash
   ./gradlew assembleDebug
   ./gradlew test
   ./gradlew lint
   ```
5. **Open a pull request** against `main`.

---

## Pull request process

- Keep PRs focused — one logical change per PR.
- Describe **what** changed and **why**. If it touches networking, storage, or
  permissions, call that out explicitly.
- Reference the issue it closes (e.g. `Closes #42`).
- Make sure `./gradlew assembleDebug` and `./gradlew test` pass.
- A maintainer will review; please respond to feedback and keep the branch up to
  date with `main`.
- Squash-merge is preferred to keep history readable.

---

## Coding standards

These mirror the project conventions in `CLAUDE.md`.

### Kotlin
- Use descriptive function names that state what they do, e.g.
  `getPermissionAccessLogsForApp(packageName: String)`.
- No magic numbers — use named `const val` in a `companion object`.
- Wrap suspend work that can fail in `runCatching` / `Result`.
- Every function should carry a short comment explaining what it does. This is an
  auditable security tool — clarity matters more than brevity.

### Architecture
- Follow MVVM + Clean Architecture: `data` → `domain` → `ui`.
- DI via Hilt. Persistence via Room.
- Compose UI must follow state-hoisting; keep composables stateless where
  possible.
- **Always write explicit Room migrations** — never rely on auto-migration or
  destructive fallback for shipped schema changes.

### Security
- Never log user data (not even with `Timber.d`).
- Use `EncryptedSharedPreferences` for any sensitive value.
- Do not add network calls without justification in the PR.

---

## Testing

- Unit-test the domain and repository layers (target 80%+ coverage there).
- Integration-test Room DAOs and WorkManager where practical.
- Manually verify monitoring accuracy on a real device for behavioral changes.

---

## Reporting bugs & security issues

- **Bugs:** open a GitHub issue with steps to reproduce, device model and Android
  version, and logs if available (scrub anything personal).
- **Security vulnerabilities:** please do **not** open a public issue. Contact the
  maintainers privately first so the issue can be addressed responsibly.

---

## Communication

Use GitHub Issues for bugs and feature discussion, and pull requests for code.
Be respectful and constructive — we're all here for the same reason.

---

By contributing, you agree that your contributions will be licensed under the
project's **GPL-3.0** license.
