# ============================================================
# ECR 容器镜像仓库
# ============================================================
# ============================================================
resource "aws_ecr_repository" "java_service" {
  name                 = "${var.project_name}-java-service"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  force_delete = false
}

# 镜像生命周期策略：只保留最近 10 个版本
resource "aws_ecr_lifecycle_policy" "java_service_lifecycle" {
  repository = aws_ecr_repository.java_service.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "保留最近 10 个镜像"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}
