# ============================================================
# S3 存储桶：前端静态网站 + 安卓 APK 制品
# ============================================================

resource "aws_s3_bucket" "frontend_android" {
  #checkov:skip=CKV_AWS_145:非用户数据，SSE-S3 够用，KMS 性价比不高
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

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}