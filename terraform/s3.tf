# 存储 Maven 构建产物的 JAR 包

resource "aws_s3_bucket" "java_artifacts" {
  #checkov:skip=CKV_AWS_145:桶里只放可重新构建的 JAR 制品，不是用户数据，SSE-S3 已经够用；
  #  上 KMS 要额外建/管一把 key、每次读写多一次 KMS API 调用，性价比不划算
  bucket = "${var.project_name}-java-artifacts-${var.environment}"
}

resource "aws_s3_bucket_versioning" "java_artifacts_versioning" {
  bucket = aws_s3_bucket.java_artifacts.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "java_artifacts_encryption" {
  bucket = aws_s3_bucket.java_artifacts.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# 禁止公开访问
resource "aws_s3_bucket_public_access_block" "java_artifacts_public_access" {
  bucket                  = aws_s3_bucket.java_artifacts.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# 生命周期规则：自动清理过期构建产物
resource "aws_s3_bucket_lifecycle_configuration" "java_artifacts_lifecycle" {
  bucket = aws_s3_bucket.java_artifacts.id

  rule {
    id     = "expire-old-builds"
    status = "Enabled"

    filter {
      prefix = "java-module/"
    }

    # 当前版本 90 天后过期
    expiration {
      days = 90
    }

    # 非当前版本（被覆盖的旧 JAR）30 天后删除
    noncurrent_version_expiration {
      noncurrent_days = 30
    }
  }
}
