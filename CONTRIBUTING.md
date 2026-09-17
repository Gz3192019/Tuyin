# Contributing to RAC-Hide

Thanks for your interest in improving RAC-Hide. This document explains how to
build, test and submit changes.

## Ground rules

RAC-Hide is deliberately small and portable. Please keep it that way:

- **No runtime dependencies.** The library must run from plain ES modules with
  no build step.
- **Keep the core DOM-free.** Everything in `src/core.js` and its imports
  (`dct`, `color`, `reed-solomon`, `interleave`, `ruler`, `bits`, `constants`)
  must run in Node and in a Web Worker. Browser APIs belong in
  `src/browser.js` only.
- **Test new behaviour.** Add or extend a test under `test/`.
- **Stay on GPL-3.0.** Contributions are accepted under the same license.

## Development

Requirements: Node.js 18 or newer.

```bash
git clone <your fork>
cd rac-hide
npm test
```

`npm test` runs the [`node:test`](https://nodejs.org/api/test.html) suite; there
are no dependencies to install.

To try the browser demo, serve the repository over HTTP (ES modules do not load
from `file://`):

```bash
python3 -m http.server 8000
# open http://localhost:8000/demo/
```

## Code style

- 2-space indentation, semicolons, single quotes.
- Prefer small, pure, well-named functions with JSDoc on exported symbols.
- Comments should explain *why*, not restate *what*.
- Match the style of the surrounding file.

## Commits and pull requests

- Use [Conventional Commits](https://www.conventionalcommits.org/) prefixes
  (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `perf:`, `chore:`).
- Keep each pull request focused on a single change.
- Fill in the pull request template and make sure `npm test` passes.

## Reporting bugs and security issues

- Functional bugs: open a GitHub issue using the bug report template.
- Security issues: **do not** open a public issue. Follow
  [`SECURITY.md`](SECURITY.md).

## License

By contributing you agree that your contributions are licensed under the
GNU General Public License v3.0 or later, as described in [`LICENSE`](LICENSE).
