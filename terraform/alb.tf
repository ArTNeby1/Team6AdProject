# ============================================================
# ALB：Java 后端和 ML 服务共用一个，按路径路由
#   /api/*  -> Java 后端（controller 本来就都挂在 /api/v1 下）
#   其余     -> ML 服务（health/extract 等接口都在根路径，没有专属前缀）
# 暂时只走 HTTP，等有域名/ACM 证书了再加 443 监听器。
# ============================================================

resource "aws_lb" "main" {
  name               = "${var.project_name}-${var.environment}"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id
}

resource "aws_lb_target_group" "java" {
  name        = "${var.project_name}-java-${var.environment}"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip" # Fargate 用 awsvpc 网络模式，目标类型必须是 ip

  health_check {
    path                = "/api/v1/health"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 30
    timeout             = 5
    matcher             = "200"
  }
}

resource "aws_lb_target_group" "ml" {
  name        = "${var.project_name}-ml-${var.environment}"
  port        = 8000
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    path                = "/health"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 30
    timeout             = 5
    matcher             = "200"
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  # 默认转发给 ML 服务：它的接口都在根路径，没有专属前缀
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.ml.arn
  }
}

resource "aws_lb_listener_rule" "java_api" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 100

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.java.arn
  }

  condition {
    path_pattern {
      values = ["/api/*"]
    }
  }
}
