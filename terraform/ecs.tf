# ============================================================
# ECS Fargate：Java 后端 + ML 服务
# 镜像统一用 :latest 标签，CI 推完新镜像后调用
#   aws ecs update-service --force-new-deployment
# 让服务滚动拉取最新镜像，Terraform 这边镜像地址本身不用变。
# ============================================================

resource "aws_ecs_cluster" "main" {
  #checkov:skip=CKV_AWS_65:course 项目规模用不上 Container Insights 的额外计费
  name = "${var.project_name}-${var.environment}"
}

# ------------------------------------------------------------
# 日志组
# ------------------------------------------------------------

resource "aws_cloudwatch_log_group" "java" {
  #checkov:skip=CKV_AWS_158:默认加密已够用，KMS 性价比不高
  #checkov:skip=CKV_AWS_338:course 项目生命周期短，14 天够用，1 年纯粹多付存储费
  name              = "/ecs/${var.project_name}-java-${var.environment}"
  retention_in_days = 14
}

resource "aws_cloudwatch_log_group" "ml" {
  #checkov:skip=CKV_AWS_158:同上
  #checkov:skip=CKV_AWS_338:同上
  name              = "/ecs/${var.project_name}-ml-${var.environment}"
  retention_in_days = 14
}

# ------------------------------------------------------------
# Task Definition：Java 后端
# ------------------------------------------------------------

resource "aws_ecs_task_definition" "java" {
  #checkov:skip=CKV_AWS_336:容器运行时可能往本地写临时文件，锁只读前需要先验证不会跑挂，这台机器没有能测的环境
  family                   = "${var.project_name}-java-${var.environment}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task_java.arn

  container_definitions = jsonencode([
    {
      name      = "java-service"
      image     = "${aws_ecr_repository.java_service.repository_url}:latest"
      essential = true
      portMappings = [
        {
          containerPort = 8080
          protocol      = "tcp"
        }
      ]
      environment = [
        { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
        { name = "CORS_ALLOWED_ORIGINS", value = "http://${aws_s3_bucket_website_configuration.frontend_web.website_endpoint}" },
        # ALB 默认转发到 ML（非 /api/*），Java 通过同一 DNS 调用 /extract-travel-info、/refine、/recommend
        { name = "AI_SERVICE_BASE_URL", value = "http://${aws_lb.main.dns_name}" }
      ]
      secrets = concat([
        { name = "DB_HOST", valueFrom = "${aws_secretsmanager_secret.db.arn}:host::" },
        { name = "DB_PORT", valueFrom = "${aws_secretsmanager_secret.db.arn}:port::" },
        { name = "DB_NAME", valueFrom = "${aws_secretsmanager_secret.db.arn}:dbname::" },
        { name = "DB_USERNAME", valueFrom = "${aws_secretsmanager_secret.db.arn}:username::" },
        { name = "DB_PASSWORD", valueFrom = "${aws_secretsmanager_secret.db.arn}:password::" },
        { name = "JWT_SECRET", valueFrom = aws_secretsmanager_secret.jwt.arn }
      ], var.google_maps_api_key_secret_arn == "" ? [] : [
        { name = "GOOGLE_MAPS_API_KEY", valueFrom = var.google_maps_api_key_secret_arn }
      ])
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.java.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "java"
        }
      }
    }
  ])

  # secrets 里引用的是 secret 的具体 key，Terraform 光看 ARN 引用看不出
  # 依赖 secret_version，这里显式声明，确保密码先写进 Secrets Manager
  depends_on = [
    aws_secretsmanager_secret_version.db,
    aws_secretsmanager_secret_version.jwt
  ]
}

# ------------------------------------------------------------
# Task Definition：ML 服务
# ------------------------------------------------------------

resource "aws_ecs_task_definition" "ml" {
  #checkov:skip=CKV_AWS_336:同上，需要先验证容器不写本地盘再改
  family                   = "${var.project_name}-ml-${var.environment}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "1024"
  memory                   = "2048"
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task_ml.arn

  container_definitions = jsonencode([
    {
      name      = "ml-service"
      image     = "${aws_ecr_repository.ml_service.repository_url}:latest"
      essential = true
      portMappings = [
        {
          containerPort = 8000
          protocol      = "tcp"
        }
      ]
      # 显式声明，不依赖 orchestrator.py/chat_filter.py 里的代码默认值：
      # 容器镜像是 python:3.11-slim，没装 Ollama，provider 如果落到代码默认值
      # 会跟着代码改动漂移，这里写死更好排查。
      # 2026-08-11：从 mock 改回 bedrock（PR #25 那次切 mock 是为了排查
      # /recommend 线上问题，排查窗口已经结束）。
      environment = [
        { name = "EXTRACT_PROVIDER", value = "bedrock" },
        { name = "RECOMMEND_PROVIDER", value = "bedrock" },
        { name = "FILTER_PROVIDER", value = "bedrock" },
        # Bedrock 的 Model access 和账单在另一个账号，容器先 AssumeRole 再调。
        # 留空则退回用 task role 自己的权限（同账号调用）。
        { name = "BEDROCK_ASSUME_ROLE_ARN", value = var.bedrock_assume_role_arn },
        # 显式写出来，不靠 bedrock_client.py 里的代码默认值 —— 代码默认值改了
        # 线上会跟着悄悄变（PR #25 踩过这个坑）。跟部署 region 不是一回事，
        # 见 variables.tf 里 bedrock_region 的说明。
        { name = "BEDROCK_REGION", value = var.bedrock_region }
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.ml.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "ml"
        }
      }
    }
  ])
}

# ------------------------------------------------------------
# Service：Java 后端
# ------------------------------------------------------------

resource "aws_ecs_service" "java" {
  #checkov:skip=CKV_AWS_333:有意不建 NAT 网关，任务跑在公有子网省成本（见 vpc.tf 顶部说明）
  name            = "${var.project_name}-java-${var.environment}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.java.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.ecs_tasks.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.java.arn
    container_name   = "java-service"
    container_port   = 8080
  }

  # 新版本部署后如果任务一直起不来（比如镜像坏了、健康检查一直不过），
  # ECS 自动回滚到上一个跑得动的版本，不会一直死循环重启。
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  depends_on = [aws_lb_listener.http]
}

# ------------------------------------------------------------
# Service：ML 服务
# ------------------------------------------------------------

resource "aws_ecs_service" "ml" {
  #checkov:skip=CKV_AWS_333:同上，有意不建 NAT 网关
  name            = "${var.project_name}-ml-${var.environment}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.ml.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.ecs_tasks.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.ml.arn
    container_name   = "ml-service"
    container_port   = 8000
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  depends_on = [aws_lb_listener.http]
}
