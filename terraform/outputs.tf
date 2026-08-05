output "java_artifacts_bucket" {
  value       = aws_s3_bucket.java_artifacts.bucket
  description = "S3 存储桶名，CI 流水线会上传 JAR 到这里"
}

output "java_ecr_repository_url" {
  value       = aws_ecr_repository.java_service.repository_url
  description = "Java 后端 ECR 仓库地址"
}

output "ml_ecr_repository_url" {
  value       = aws_ecr_repository.ml_service.repository_url
  description = "ML 服务 ECR 仓库地址"
}

output "github_actions_role_arn" {
  value       = aws_iam_role.github_actions.arn
  description = "GitHub Actions 使用的 IAM 角色 ARN"
}

output "frontend_android_bucket" {
  value       = aws_s3_bucket.frontend_android.bucket
  description = "S3 存储桶名，前端静态文件和安卓 APK 存放处"
}