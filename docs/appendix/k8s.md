# Appendix C — Kubernetes Primer

Kubernetes is an application runtime platform. This course deploys to Fly.io for the main path, but the Spring Boot application shape maps cleanly to Kubernetes.

A minimal deployment has these pieces:

- `Deployment`: runs and rolls out your application pods.
- `Service`: gives those pods a stable network name.
- `ConfigMap`: stores non-secret configuration.
- `Secret`: stores credentials and tokens.
- Probes: tell Kubernetes when the app is alive and ready for traffic.

Spring Boot Actuator readiness and liveness probes fit Kubernetes directly:

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
```

Then point Kubernetes probes at:

```text
/actuator/health/liveness
/actuator/health/readiness
```

A production Kubernetes setup also needs image publishing, ingress, TLS certificates, resource requests and limits, database provisioning, secret rotation, logging, metrics, tracing, and rollout policy. Treat Kubernetes as platform engineering work, not as a different way to run `java -jar`.

