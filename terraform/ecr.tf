# ============================================================
# ECR 容器镜像仓库
# ============================================================

resource "aws_ecr_repository" "java_service" {
  #checkov:skip=CKV_AWS_136:镜像非敏感数据，默认加密已够用，KMS 性价比不高
  #checkov:skip=CKV_AWS_51:CI 需要反复推 :latest 标签，immutable 会跟这个流程冲突（之前真的踩过这个坑）
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
  #checkov:skip=CKV_AWS_136:同上，镜像非敏感数据，默认加密够用
  #checkov:skip=CKV_AWS_51:同上，CI 需要反复推 :latest 标签
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
