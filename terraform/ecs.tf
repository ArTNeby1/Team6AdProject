# ============================================================
# ECS Fargate：Java 后端 + ML 服务
# 镜像统一用 :latest 标签，CI 推完新镜像后调用
#   aws ecs update-service --force-new-deployment
# 让服务滚动拉取最新镜像，Terraform 这边镜像地址本身不用变。
# ============================================================

resource "aws_ecs_cluster" "main" {
  name = "${var.project_name}-${var.environment}"
}

# ------------------------------------------------------------
# 日志组
# ------------------------------------------------------------

resource "aws_cloudwatch_log_group" "java" {
  name              = "/ecs/${var.project_name}-java-${var.environment}"
  retention_in_days = 14
}

resource "aws_cloudwatch_log_group" "ml" {
  name              = "/ecs/${var.project_name}-ml-${var.environment}"
  retention_in_days = 14
}

# ------------------------------------------------------------
# Task Definition：Java 后端
# ------------------------------------------------------------

resource "aws_ecs_task_definition" "java" {
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
        { name = "SPRING_PROFILES_ACTIVE", value = "prod" }
      ]
      secrets = [
        { name = "DB_HOST", valueFrom = "${aws_secretsmanager_secret.db.arn}:host::" },
        { name = "DB_PORT", valueFrom = "${aws_secretsmanager_secret.db.arn}:port::" },
        { name = "DB_NAME", valueFrom = "${aws_secretsmanager_secret.db.arn}:dbname::" },
        { name = "DB_USERNAME", valueFrom = "${aws_secretsmanager_secret.db.arn}:username::" },
        { name = "DB_PASSWORD", valueFrom = "${aws_secretsmanager_secret.db.arn}:password::" },
        { name = "JWT_SECRET", valueFrom = aws_secretsmanager_secret.jwt.arn }
      ]
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

  depends_on = [aws_lb_listener.http]
}

# ------------------------------------------------------------
# Service：ML 服务
# ------------------------------------------------------------

resource "aws_ecs_service" "ml" {
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

  depends_on = [aws_lb_listener.http]
}
