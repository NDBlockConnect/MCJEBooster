# 01 - MCJEBooster Versioning & Release Conventions

> English is the canonical documentation; `docs/zh/` holds the Chinese copy.
> Conventions adopted from the Aprism Loader project (NDBlockConnect/Aprism).

**Applies to:** v26.0-Alpha.1 and later.

## Version format

```
v<Year>.<minor>[-Alpha.<n>]
```

- **Major line** — one per calendar year. `v26` is the 2026 line and contains
  ten minors: `v26.0` ... `v26.9`.
- **Development (public)** — within each minor, `v26.0-Alpha.1` ...
  `v26.0-Alpha.9` are shipped as GitHub **Pre-Releases**. Normal cadence is one
  Alpha every two weeks; during intensive development windows the cadence may
  be compressed.
- **Release candidate** — `Alpha.9` is the release candidate. **There is never
  an Alpha.10.**
- **Minor official** — the bare version number (e.g. `v26.0`) is the official
  GA **Release** for that minor.
- **Beta** — not planned.
- **Interface contract** — monotonic increment only; deprecation is allowed
  with notice.

## Tags and artifacts

| Item | Format | Example |
|---|---|---|
| Git tag | identical to version string | `v26.0-Alpha.1`, `v26.0` |
| JAR artifact | `MCJEBooster-<version>.jar` | `MCJEBooster-v26.0-Alpha.1.jar` |
| Checksums | `checksums.txt` (SHA-256), one entry per artifact | — |

Every release carries a SHA-256 `checksums.txt`. Verify before running:

```bash
sha256sum -c checksums.txt
```

## Release types

| Version shape | GitHub release kind |
|---|---|
| `v26.x-Alpha.n` | Pre-Release |
| `v26.x` (bare) | Release (GA) |

## Commit message convention

```
<type>(<scope>): v<version> - <summary> (<details>; <test count>)
```

- **type** ∈ `feat`, `fix`, `refactor`, `chore`, `release`, `docs`, `test`
- **scope** — module or concern, e.g. `lowlevel`, `client`, `hybrid`, `repo`, `ci`
- feature commits carry the target version and the test count
  (e.g. `10 tests, 42 total`); release commits use `release: <version>`.

Examples from this repository:

```
feat(repo): v26.0-Alpha.1 - central version constant + deterministic build
feat(client): v26.1-Alpha.1 - client-side detection seam (SideDetector; 6 tests, 30 total)
release: v26.0
```

## Single source of truth

The runtime version string lives in exactly one place:
`com.mcjebooster.util.BoosterVersion.VERSION`. Bump it, rebuild with
`scripts/build.sh`, tag, and release. No other file may hard-code a different
release version.
