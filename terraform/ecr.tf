# ============================================================
# ECR 容器镜像仓库
# ============================================================

resource "aws_ecr_repository" "java_service" {
  name = "${var.project_name}-java-service"

  image_tag_mutability = "MUTABLE"

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

# ============================================================
# ML 服务 ECR 仓库
# ============================================================
# 仓库名与 ci-python-ml-agent.yml 里的 ECR_REPOSITORY 保持字面一致
# （历史原因命名上没有跟 project_name 变量对齐，注意别改错）。
resource "aws_ecr_repository" "ml_service" {
  name                 = "adproject-ml-service"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  force_delete = false
}

resource "aws_ecr_lifecycle_policy" "ml_service_lifecycle" {
  repository = aws_ecr_repository.ml_service.name

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
