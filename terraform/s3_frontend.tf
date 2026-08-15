# ============================================================
# S3 存储桶：前端静态网站 + 安卓 APK 制品
# ============================================================

resource "aws_s3_bucket" "frontend_android" {
  #checkov:skip=CKV_AWS_145:桶里只放可重新构建的 APK 制品，不是用户数据，SSE-S3 已经够用；
  #  上 KMS 要额外建/管一把 key、每次读写多一次 KMS API 调用，性价比不划算
  bucket = "${var.project_name}-frontend-android-${var.environment}"
}

# 版本控制
resource "aws_s3_bucket_versioning" "frontend_android_versioning" {
  bucket = aws_s3_bucket.frontend_android.id
  versioning_configuration {
    status = "Enabled"
  }
}

# 加密
resource "aws_s3_bucket_server_side_encryption_configuration" "frontend_android_encryption" {
  bucket = aws_s3_bucket.frontend_android.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# 禁止公开访问
resource "aws_s3_bucket_public_access_block" "frontend_android_public_access" {
  bucket                  = aws_s3_bucket.frontend_android.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# 生命周期：APK 90 天后过期，旧版本 30 天删除
resource "aws_s3_bucket_lifecycle_configuration" "frontend_android_lifecycle" {
  bucket = aws_s3_bucket.frontend_android.id

  rule {
    id     = "expire-old-artifacts"
    status = "Enabled"

    expiration {
      days = 90
    }

    noncurrent_version_expiration {
      noncurrent_days = 30
    }
  }
}