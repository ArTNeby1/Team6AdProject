variable "aws_region" {
  description = "AWS 区域"
  type        = string
  default     = "us-east-1"
}

variable "aws_account_id" {
  description = "AWS 账户 ID（12 位数字），用于防止误操作到其他账户"
  type        = string
  default     = "590183790873"
}

variable "project_name" {
  description = "项目名称，用于所有资源命名"
  type        = string
  default     = "ad-project"
}

variable "github_org" {
  description = "GitHub 组织名或用户名"
  type        = string
  default     = "ArTNeby1"
}

variable "github_repo" {
  description = "GitHub 仓库名"
  type        = string
  default     = "Team6AdProject"
}

variable "environment" {
  description = "环境名（dev/staging/prod）"
  type        = string
  default     = "dev"
}
