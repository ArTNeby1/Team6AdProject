# ============================================================
# ECS 运行时用的 IAM 角色
# 区分两种角色，职责不同：
#   - Execution Role：ECS Agent 自己用，拉镜像、写 CloudWatch 日志
#   - Task Role：容器里跑的应用代码用，按服务分开、各给各的最小权限
# ============================================================

data "aws_iam_policy_document" "ecs_tasks_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

# ------------------------------------------------------------
# Execution Role：两个服务共用，只负责拉镜像 + 写日志
# ------------------------------------------------------------

resource "aws_iam_role" "ecs_task_execution" {
  name               = "${var.project_name}-ecs-execution-${var.environment}"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume_role.json
}

resource "aws_iam_role_policy_attachment" "ecs_task_execution" {
  role       = aws_iam_role.ecs_task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# ECS Agent 启动容器前要从 Secrets Manager 取值注入环境变量，
# 这个权限必须挂在 Execution Role 上（不是 Task Role）。
resource "aws_iam_role_policy" "ecs_task_execution_secrets" {
  name = "read-secrets"
  role = aws_iam_role.ecs_task_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = ["secretsmanager:GetSecretValue"]
        Resource = concat([
          aws_secretsmanager_secret.db.arn,
          aws_secretsmanager_secret.jwt.arn
        ], var.google_maps_api_key_secret_arn == "" ? [] : [
          var.google_maps_api_key_secret_arn
        ])
      }
    ]
  })
}

# ------------------------------------------------------------
# Task Role：Java 后端 —— 目前应用代码不调用别的 AWS 服务，先给空权限，
# 以后要用（比如传文件到 S3）再往这个角色上加 policy。
# ------------------------------------------------------------

resource "aws_iam_role" "ecs_task_java" {
  name               = "${var.project_name}-ecs-task-java-${var.environment}"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume_role.json
}

# ------------------------------------------------------------
# Task Role：ML 服务 —— 需要调用 Bedrock 推理
# ------------------------------------------------------------

resource "aws_iam_role" "ecs_task_ml" {
  name               = "${var.project_name}-ecs-task-ml-${var.environment}"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume_role.json
}

resource "aws_iam_role_policy" "ecs_task_ml_bedrock" {
  name = "bedrock-invoke"
  role = aws_iam_role.ecs_task_ml.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "bedrock:InvokeModel",
          "bedrock:InvokeModelWithResponseStream"
        ]
        # Foundation model 不属于某个账号，ARN 里没有 account id；
        # 先放开到所有 region/模型，等选定具体模型了再收紧到具体 model ARN。
        Resource = "arn:aws:bedrock:*::foundation-model/*"
      }
    ]
  })
}

# 跨账号调 Bedrock：实际的 InvokeModel 权限来自 483528439116 账号里那个角色，
# 这里只需要允许本账号的 ML task role 去 AssumeRole。上面那条同账号的
# bedrock-invoke 保留着不删 —— 万一以后本账号自己开通了 Model access，
# 把 bedrock_assume_role_arn 置空就能直接切回同账号调用，不用改 IAM。
resource "aws_iam_role_policy" "ecs_task_ml_assume_bedrock" {
  count = var.bedrock_assume_role_arn == "" ? 0 : 1

  name = "assume-cross-account-bedrock"
  role = aws_iam_role.ecs_task_ml.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = "sts:AssumeRole"
        Resource = var.bedrock_assume_role_arn
      }
    ]
  })
}
