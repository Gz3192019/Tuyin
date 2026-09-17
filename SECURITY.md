# Security Policy

## Scope

RAC-Hide is a **steganography** library: it hides the existence of a payload,
it does **not** encrypt it. Anyone who knows the algorithm can recover an
embedded payload. If your threat model requires confidentiality, encrypt the
data before embedding it.

The library performs no network requests and runs entirely on the local
machine.

## Supported versions

Security fixes are applied to the latest release on the `main` branch.

| Version | Supported |
|---------|:---------:|
| 1.x     | ✅        |

## Reporting a vulnerability

Please **do not** report security issues through public GitHub issues.

Report privately by email to **twb08544588@163.com** with:

- a description of the issue and its impact,
- steps to reproduce (a minimal example is ideal),
- the affected version or commit.

You will receive an acknowledgement as soon as possible, and we will coordinate
a fix and disclosure timeline with you.

## Out of scope

- The inherent detectability or recoverability of steganographic payloads.
- Use of the software for unlawful purposes; see the disclaimer in the
  [README](README.md#security-and-ethics).
