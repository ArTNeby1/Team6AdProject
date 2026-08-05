# ============================================================
# RDS MySQL：Java 后端的数据库
# 放在和 ECS 任务同样的公有子网里，但 publicly_accessible = false，
# 只允许 ECS 任务安全组访问 —— 不建独立的私有子网/NAT，省成本。
# ============================================================

resource "aws_security_group" "rds" {
  name        = "${var.project_name}-rds-${var.environment}"
  description = "RDS security group: only allow MySQL from ECS tasks"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "MySQL from ECS tasks"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_tasks.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-rds-sg-${var.environment}"
  }
}

resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-${var.environment}"
  subnet_ids = aws_subnet.public[*].id

  tags = {
    Name = "${var.project_name}-db-subnet-group-${var.environment}"
  }
}

# RDS 主密码不能含 '/', '"', '@', 空格；这里用安全字符集随机生成，
# 生成后直接进 Secrets Manager，不会出现在 Terraform 输出或代码里。
resource "random_password" "db" {
  length           = 24
  special          = true
  override_special = "!#%^*()-_=+"
}

resource "aws_db_instance" "main" {
  identifier     = "${var.project_name}-${var.environment}"
  engine         = "mysql"
  engine_version = "8.0.46"
  instance_class = "db.t4g.micro"

  allocated_storage      = 20
  storage_type           = "gp3"
  db_name                = "LoomyTrip"
  username               = "loomyadmin"
  password               = random_password.db.result
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  publicly_accessible = false
  multi_az            = false

  # 学生/小团队项目，不需要跨环境保留备份体系；真要保数据再开
  backup_retention_period = 1
  skip_final_snapshot     = true
  deletion_protection     = false

  tags = {
    Name = "${var.project_name}-mysql-${var.environment}"
  }
}

# ------------------------------------------------------------
# Secrets Manager：数据库连接信息，ECS 任务从这里读取
# ------------------------------------------------------------

resource "aws_secretsmanager_secret" "db" {
  name = "${var.project_name}/${var.environment}/db"
}

resource "aws_secretsmanager_secret_version" "db" {
  secret_id = aws_secretsmanager_secret.db.id
  secret_string = jsonencode({
    host     = aws_db_instance.main.address
    port     = tostring(aws_db_instance.main.port)
    dbname   = aws_db_instance.main.db_name
    username = aws_db_instance.main.username
    password = random_password.db.result
  })
}

# JWT 签名密钥：应用默认值只适合本地开发，生产环境必须换成随机值
resource "random_password" "jwt_secret" {
  length  = 48
  special = false
}

resource "aws_secretsmanager_secret" "jwt" {
  name = "${var.project_name}/${var.environment}/jwt-secret"
}

resource "aws_secretsmanager_secret_version" "jwt" {
  secret_id     = aws_secretsmanager_secret.jwt.id
  secret_string = random_password.jwt_secret.result
}
