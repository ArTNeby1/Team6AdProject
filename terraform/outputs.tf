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

output "alb_dns_name" {
  value       = aws_lb.main.dns_name
  description = "ALB 公网地址，/api/* 转发给 Java 后端，其余转发给 ML 服务"
}

output "ecs_cluster_name" {
  value       = aws_ecs_cluster.main.name
  description = "ECS 集群名，CI 里 update-service 要用"
}

output "ecs_java_service_name" {
  value       = aws_ecs_service.java.name
  description = "Java 后端 ECS Service 名"
}

output "ecs_ml_service_name" {
  value       = aws_ecs_service.ml.name
  description = "ML 服务 ECS Service 名"
}

output "rds_endpoint" {
  value       = aws_db_instance.main.address
  description = "RDS 地址（不含密码，密码在 Secrets Manager 里）"
}

output "db_secret_name" {
  value       = aws_secretsmanager_secret.db.name
  description = "数据库连接信息存放的 Secrets Manager 名称"
}