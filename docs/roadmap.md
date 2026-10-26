# Painting Ecommerce — Project Roadmap

## Phase 1: MVP catalogue (current)
- [x] Spring Boot project scaffold (Java 17 + Maven)
- [x] Painting entity and repository
- [x] REST endpoints to create, list, view, search, update, and delete paintings
- [x] Request validation and basic error responses
- [x] H2 database for local development

## Phase 2: Catalogue improvements
- [x] Add pagination (sorting is still open)
- [ ] Add sorting and filters: artist, price range, medium, style, availability
- [ ] Add categories and tags
- [x] Add DTOs so persistence entities are not exposed directly
- [x] Add integration tests for API and validation

## Phase 3: Customers and security
- [x] Add customer registration and login
- [x] Add Spring Security and role-based access (CUSTOMER / ADMIN)
- [x] Restrict painting create/update/delete to ADMIN
- [ ] Add login rate limiting / lockout, refresh tokens and token revocation
- [ ] Add password reset and email verification
- [ ] Add user profile and address management

## Phase 4: Cart and orders
- [ ] Add shopping cart and cart items
- [ ] Add checkout and order lifecycle (PENDING, PAID, SHIPPED, DELIVERED, CANCELLED)
- [ ] Reserve/decrement stock safely during order placement
- [ ] Add order history and admin order management

## Phase 5: Payments and artwork images
- [ ] Integrate a payment provider using sandbox/test mode
- [ ] Verify payment webhooks and make payment processing idempotent
- [ ] Upload painting images to Amazon S3; store object URLs/keys in the database
- [ ] Validate file type and size; do not make uploaded files public by default

## Phase 6: Production readiness
- [x] Replace H2 with PostgreSQL locally in Docker, with a persistent volume (see [docker.md](docker.md))
- [ ] Move the database to Amazon RDS (managed backups, Multi-AZ, credentials from Secrets Manager)
- [ ] Store secrets in environment variables / AWS Secrets Manager
- [ ] Add structured logs, health checks, metrics, and tracing
- [x] Add Dockerfile and container build (images, compose and a CI smoke test are written; see [docker.md](docker.md). First verified by the CI `docker` job, not yet built locally)
- [ ] Deploy container to Amazon ECS Fargate behind an Application Load Balancer
- [ ] Use Amazon ECR for images and CloudWatch for logs/alarms
- [ ] Configure a custom domain and HTTPS
- [ ] Set AWS Budget alerts before creating billable resources

## Phase 7: CI/CD and quality
- [x] Add GitHub Actions CI (tests + build)
- [x] Use GitHub Actions OIDC for AWS access; avoid long-lived AWS keys (workflow and trust-policy guide are in place: see [aws-oidc.md](aws-oidc.md); it activates once an AWS role is created and `AWS_ROLE_ARN` is set)
- [x] Add dependency/security scanning (Dependabot, CodeQL, dependency review on pull requests, `npm audit` in CI)
- [x] Add API documentation with OpenAPI/Swagger (dev profile: `/swagger-ui.html`)
- [x] Add load tests and performance targets (k6, see [performance.md](performance.md))

## Suggested build order
1. Finish catalogue tests and DTOs.
2. ~~Add PostgreSQL~~ (done locally; managed RDS is the remaining step).
3. Add authentication and admin authorization.
4. Implement cart and order workflows.
5. Add sandbox payments and S3 image storage.
6. Containerize, automate CI/CD, and deploy to AWS.

## Definition of done for each phase
- Tests pass locally.
- API behavior and error cases are documented.
- Secrets are not committed.
- Changes are small enough to review and can be run from a clean checkout.
