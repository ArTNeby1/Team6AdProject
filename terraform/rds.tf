# ============================================================
# RDS MySQL：Java 后端的数据库
# 放在和 ECS 任务同样的公有子网里，但 publicly_accessible = false，
# 只允许 ECS 任务安全组访问 —— 不建独立的私有子网/NAT，省成本。
# ============================================================

resource "aws_security_group" "rds" {
  #checkov:skip=CKV_AWS_382:RDS 托管服务需要一定出站访问（指标上报/参数组同步等），收紧范围没把握不会误伤
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
    description = "Allow all outbound (managed service needs it for AWS-internal traffic)"
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
  #checkov:skip=CKV_AWS_157:课程项目单 AZ 就够，Multi-AZ 大致双倍费用，之前已经明确选过单 AZ
  #checkov:skip=CKV_AWS_226:自动小版本升级可能在演示前触发重启，可预测性优先于自动打补丁
  #checkov:skip=CKV_AWS_293:项目会整体 destroy/重建省成本，删除保护会挡住这个操作
  #checkov:skip=CKV_AWS_161:改成 IAM 认证要动后端数据源配置，是应用层改动，不在这次 IaC 修复范围内
  #checkov:skip=CKV_AWS_118:增强监控要多建 IAM 角色+更细粒度指标计费，这个规模用不上
  #checkov:skip=CKV_AWS_16:storage_encrypted 在 RDS 上不能原地切换，加了会触发销毁重建——这是线上正在跑的库，有真实用户数据，绝不能顺手改，需要先做好快照/迁移预案
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

  enabled_cloudwatch_logs_exports = ["error", "general", "slowquery"]

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
  #checkov:skip=CKV_AWS_149:默认 AWS 管理的 key 已经加密，自建 CMK 多一层管理成本，对这个内容性价比不高
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
  #checkov:skip=CKV_AWS_149:同上，默认加密已经够用
  name = "${var.project_name}/${var.environment}/jwt-secret"
}

resource "aws_secretsmanager_secret_version" "jwt" {
  secret_id     = aws_secretsmanager_secret.jwt.id
  secret_string = random_password.jwt_secret.result
}
