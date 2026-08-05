# ============================================================
# 安全组：ALB 对公网开放，ECS 任务只信任 ALB
# ============================================================

resource "aws_security_group" "alb" {
  name        = "${var.project_name}-alb-${var.environment}"
  description = "ALB：对公网开放 80 端口"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP from internet"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-alb-sg-${var.environment}"
  }
}

resource "aws_security_group" "ecs_tasks" {
  name        = "${var.project_name}-ecs-tasks-${var.environment}"
  description = "ECS 任务：容器端口只允许来自 ALB 的流量"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Java 后端端口，仅 ALB 可访问"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  ingress {
    description     = "ML 服务端口，仅 ALB 可访问"
    from_port       = 8000
    to_port         = 8000
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-ecs-tasks-sg-${var.environment}"
  }
}
