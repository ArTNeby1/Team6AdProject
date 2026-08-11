variable "aws_region" {
  description = "AWS 区域"
  type        = string
  default     = "ap-southeast-1"
}

variable "aws_account_id" {
  description = "AWS 账户 ID（12 位数字），用于防止误操作到其他账户"
  type        = string
  default     = "998976076574"
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

# Bedrock 的 Model access 和账单都在另一个账号（483528439116）上，不在本项目
# 账号里。ML 容器会先 AssumeRole 到这个角色再调 Bedrock，这样不用把任何长期
# access key 存进本项目账号。留空 = 不跨账号，直接用 task role 自己的权限。
# 这个角色需要在 483528439116 里手动创建，信任策略只信任本项目的 ML task role。
variable "bedrock_assume_role_arn" {
  description = "跨账号调用 Bedrock 用的角色 ARN（留空则不跨账号）"
  type        = string
  default     = "arn:aws:iam::483528439116:role/Team6BedrockInvoke"
}
