# EC2 Docker Deployment

GitHub Actions workflow: `.github/workflows/deploy-ec2.yml`

Required repository secrets:

```text
EC2_HOST=3.35.27.121
EC2_USER=ubuntu
EC2_SSH_KEY=<private key content>
BACKEND_ENV=<production .env content>
```

Recommended `BACKEND_ENV` shape:

```properties
MYSQL_CONTAINER_NAME=cowork-mysql
MYSQL_ROOT_PASSWORD=<root-password>
MYSQL_DATABASE=cowork
MYSQL_USERNAME=cowork
MYSQL_PASSWORD=<app-password>
APP_IMAGE=cowork-backend:latest
APP_CONTAINER_NAME=cowork-backend
SPRING_PROFILES_ACTIVE=prod
HIBERNATE_DDL_AUTO=validate
JWT_SECRET=<at-least-32-byte-secret>
SERVER_PORT=8080
FILE_STORAGE_BASE_PATH=/app/uploads
FRONTEND_URL=https://d3enhw6vmzgeun.cloudfront.net
SSO_TEMP_TOKEN_EXPIRY=300
SSO_ALLOW_UNVERIFIED_FALLBACK=false
```

The workflow builds the Spring Boot jar on GitHub Actions, uploads the deployment bundle to
`/opt/cowork/backend`, and restarts Docker Compose on the EC2 host.
