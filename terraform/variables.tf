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

# 注意这个跟 aws_region 是两回事，不要跟着改：aws_region 是这套基础设施部署在
# 哪（ap-southeast-1），这个是去哪个 region 调 Bedrock。Model access 是按
# 「账号 + region」开通的，483528439116 的 Nova Lite 是在 us-east-1 批的，
# 所以跨 region 调用是故意的，不是配错了。
variable "bedrock_region" {
  description = "调用 Bedrock 的 region（跟部署 region 无关）"
  type        = string
  default     = "us-east-1"
}

# Java 后端调 Google Routes API（RoutingClientHttp）用的 key。跟 db/jwt 密钥同一个
# 模式：裸值传进来，由 Terraform 自己建 Secrets Manager secret 并写版本，不用先在
# 控制台手动建好 secret 再把 ARN 粘过来（那种做法容易漂移、容易忘）。留空 = ECS
# 任务不挂这个环境变量，RoutingClientHttp 会降级用直线距离估算，不会报错。
variable "google_maps_api_key" {
  description = "Google Maps Platform API key（需要开通 Routes API），写进 Secrets Manager"
  type        = string
  sensitive   = true
  default     = ""
}
