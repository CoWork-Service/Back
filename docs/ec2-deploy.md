# EC2 Docker Deployment

GitHub Actions workflow: `.github/workflows/deploy-ec2.yml`

Required repository secrets:

```text
EC2_HOST=3.35.27.121
EC2_USER=ubuntu
EC2_SSH_KEY=<private key content>
BACKEND_ENV=<production .env content>
FILE_STORAGE_TYPE=s3
AWS_REGION=ap-northeast-2
AWS_S3_BUCKET=cowork-backend-uploads-396868033125
AWS_ACCESS_KEY_ID=<aws access key id>
AWS_SECRET_ACCESS_KEY=<aws secret access key>
CLOVA_OCR_SECRET_KEY=<clova ocr secret key>
CLOVA_OCR_INVOKE_URL=<clova ocr invoke url>
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

Uploaded files are stored in the private S3 bucket configured by `AWS_S3_BUCKET`.
The application stores S3 metadata such as object key, original name, content type, size, module,
and cohort ID in the `stored_files` MySQL table. Public S3 access stays blocked; existing
`/uploads/{storagePath}` URLs are served by the backend from S3.
