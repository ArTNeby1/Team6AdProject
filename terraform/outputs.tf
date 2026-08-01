output "java_artifacts_bucket" {
  value       = aws_s3_bucket.java_artifacts.bucket
  description = "S3 存储桶名，CI 流水线会上传 JAR 到这里"
}

output "java_ecr_repository_url" {
  value       = aws_ecr_repository.java_service.repository_url
  description = "ECR 仓库地址"
}

output "github_actions_role_arn" {
  value       = aws_iam_role.github_actions.arn
  description = "GitHub Actions 使用的 IAM 角色 ARN"
}
