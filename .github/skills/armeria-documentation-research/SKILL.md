---
name: armeria-documentation-research
description: Research guide for LINE/armeria documentation, API references, and release notes. Use this when asked to investigate Armeria behavior, locate official docs, or answer questions about Armeria features used by this plugin.
---

# Armeria documentation research

When researching LINE/armeria, prefer primary sources and verify answers before responding.

## Source priority

Use sources in this order:

1. Official documentation pages on `https://armeria.dev/docs/`
2. Documentation source files in `line/armeria/site-new/src/content/docs/`
3. Release notes in `line/armeria/site-new/src/content/release-notes/` for version-specific behavior
4. Armeria source code and API definitions in `line/armeria`
5. Root-level project docs such as `README.md` and `CONTRIBUTING.md`

If the public site is unavailable, fall back to the documentation source files in the `line/armeria` repository.

## Recommended entry points

Start from the most specific page that matches the question:

- Getting started:
  - `/docs/`
  - `/docs/setup`
- Server features:
  - `/docs/server/basics`
  - `/docs/server/annotated-service`
  - `/docs/server/docservice`
  - `/docs/server/grpc`
  - `/docs/server/thrift`
  - `/docs/server/decorator`
  - `/docs/server/timeouts`
- Client features:
  - `/docs/client/http`
  - `/docs/client/grpc`
  - `/docs/client/decorator`
  - `/docs/client/retry`
  - `/docs/client/service-discovery`
  - `/docs/client/timeouts`
- Advanced topics:
  - `/docs/advanced/kotlin`
  - `/docs/advanced/request-context`
  - `/docs/advanced/threading-model`
  - `/docs/advanced/unit-testing`
  - `/docs/advanced/understanding-timeouts`
  - `/docs/advanced/spring-boot-integration`
- Tutorials:
  - `/docs/tutorials/grpc`
  - `/docs/tutorials/rest`
  - `/docs/tutorials/thrift`

For questions related to this plugin, check these pages early:

- `/docs/server/annotated-service`
- `/docs/server/docservice`
- `/docs/server/grpc`
- `/docs/server/thrift`

## Research workflow

1. Identify the exact Armeria topic and, if relevant, the version range in question.
2. Read the most relevant documentation page first, then follow linked pages for related constraints or examples.
3. If the answer depends on API details, confirm it in Armeria source code or API documentation.
4. If behavior may have changed, inspect release notes to find when it changed.
5. Cross-check conflicting information before answering.

## Response requirements

When answering:

- Prefer a concise conclusion first
- Cite the exact documentation page URL or repository path you used
- Call out whether the answer comes from current docs, release notes, or source code
- Mention version-specific limitations when the evidence is version-bound
- Say explicitly when the documentation does not confirm something

## Avoid

- Do not guess from memory when you can verify from docs or source
- Do not rely on blog posts or secondary summaries before checking Armeria's official materials
- Do not treat examples from old release notes as current behavior without cross-checking the latest docs or source
