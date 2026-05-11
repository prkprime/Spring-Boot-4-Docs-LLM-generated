# Part V — Production-ready

Eleven chapters that take the app from "runs on my laptop" to "runs on a server with observability, tests, containers, CI, and a deploy target."

By the end of Part V you will have:

- Profiles, externalized configuration, and a sensible secret strategy.
- Structured JSON logs with correlation IDs.
- Actuator endpoints exposing health, info, and operational data.
- Metrics in Prometheus format and traces in OpenTelemetry, both via Micrometer.
- Graceful shutdown that drains in-flight requests cleanly.
- Slice tests, full-stack Testcontainers tests, and a clear testing pyramid.
- A working understanding of virtual-thread behaviour under load.
- A Docker image you can run anywhere.
- A GitHub Actions matrix that builds, tests, and publishes the image to GHCR.
- A continuous deploy to Fly.io with environment + secret wiring.

Chapters:

37. [Profiles & Externalized Config](37-profiles-config.md)
38. [Logging — Structured JSON](38-logging.md)
39. [Actuator](39-actuator.md)
40. [Observability — Metrics & Traces](40-observability.md)
41. [Graceful Shutdown](41-graceful-shutdown.md)
42. [Testing II — Slice Tests](42-testing-slices.md)
43. [Testing III — Testcontainers](43-testing-testcontainers.md)
44. [Virtual Threads in Depth](44-virtual-threads.md)
45. [Docker](45-docker.md)
46. [CI — GitHub Actions](46-ci-github-actions.md)
47. [CD — Fly.io](47-cd-flyio.md)
