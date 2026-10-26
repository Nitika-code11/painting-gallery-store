# Deploying to AWS without long-lived keys (GitHub Actions OIDC)

The workflow [`.github/workflows/aws-oidc.yml`](../.github/workflows/aws-oidc.yml) logs in to AWS with a
short-lived, automatically issued identity instead of an access key stored in GitHub.

> **Status:** the workflow and policies below are ready, but they have **not been exercised**: no AWS account is
> connected to this repository yet. Follow the steps once to activate it.

## Why OIDC instead of access keys

| | Stored access key | OIDC |
| --- | --- | --- |
| Secret in GitHub | Yes (a leak gives access until someone notices) | None |
| Lifetime of credentials | Until manually rotated | Minutes, per job |
| Scoped to this repo / branch | No | Yes, by the role's trust policy |

## One-time setup in AWS

1. **Add GitHub as an identity provider** (IAM → Identity providers → Add provider → OpenID Connect):
   - Provider URL: `https://token.actions.githubusercontent.com`
   - Audience: `sts.amazonaws.com`
2. **Create an IAM role** for the pipeline with this *trust policy*. The `sub` condition is the important line: only
   this repository, only the `main` branch, can assume the role.

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Effect": "Allow",
         "Principal": { "Federated": "arn:aws:iam::<ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com" },
         "Action": "sts:AssumeRoleWithWebIdentity",
         "Condition": {
           "StringEquals": {
             "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
             "token.actions.githubusercontent.com:sub": "repo:Nitika-code11/painting-gallery-store:ref:refs/heads/main"
           }
         }
       }
     ]
   }
   ```

3. **Attach least-privilege permissions** to the role, only what the pipeline needs. For the planned deployment
   (ECR image push, ECS service update) that means ECR push on one repository and `ecs:UpdateService` on one service,
   not `AdministratorAccess`.
4. **Tell GitHub the role** (repository → Settings → Secrets and variables → Actions → *Variables*):
   - `AWS_ROLE_ARN` = the role's ARN
   - `AWS_REGION` = e.g. `ap-south-1`
5. Run **Actions → AWS OIDC check → Run workflow**. It should print the assumed role from
   `aws sts get-caller-identity`.

## Rules that keep this safe

- Never widen `sub` to `repo:Nitika-code11/*` or drop the branch: any workflow in scope could then assume the role.
  For pull-request builds use a separate, read-only role.
- Add a GitHub *Environment* (e.g. `production`) with required reviewers, and match it in `sub`
  (`repo:...:environment:production`) for deploys that need a human approval.
- Before creating billable resources, set an AWS Budget alert (see the roadmap).
