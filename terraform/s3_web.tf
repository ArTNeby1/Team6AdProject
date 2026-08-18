# ============================================================
# S3 存储桶：Web 前端静态网站（独立于 frontend_android 那个桶）
# ============================================================
# 单独开桶主要是因为 Vite 打包默认假设部署在域名根路径（vite.config.js
# 没设 base），构建产物里 JS/CSS 引用的都是根路径 /assets/...。如果跟
# Android APK 挤在同一个桶的 web/ 子路径下，浏览器会去桶根路径找这些
# 静态资源，404，页面空白。独立一个桶、内容放根路径，就没有这个问题。
#
# 这个桶只装前端构建产物（没有用户数据），走 S3 静态网站托管、整桶公开
# 只读，先不上 CloudFront/HTTPS——课程演示够用，以后要接自定义域名/HTTPS
# 再补。

resource "aws_s3_bucket" "frontend_web" {
  #checkov:skip=CKV2_AWS_6:公开静态网站托管，锁死会直接把网站锁没
  #checkov:skip=CKV_AWS_145:非用户数据，SSE-S3 够用，KMS 性价比不高
  #checkov:skip=CKV_AWS_144:内容每次 CI 都会重新生成，不需要跨区域复制
  bucket = "${var.project_name}-frontend-web-${var.environment}"
}

# 版本控制：这个桶每次部署都是 aws s3 sync --delete 整体覆盖，开了版本控制之后
# 万一某次发布出问题，能直接从 S3 控制台/CLI 回滚到上一个版本
resource "aws_s3_bucket_versioning" "frontend_web_versioning" {
  bucket = aws_s3_bucket.frontend_web.id
  versioning_configuration {
    status = "Enabled"
  }
}

# 生命周期：只清理被覆盖后的旧版本，当前（最新）版本不设过期——那是线上正在跑的网站，
# 不能被自动删掉。30 天足够回滚窗口，避免旧版本一直堆积占用存储。
resource "aws_s3_bucket_lifecycle_configuration" "frontend_web_lifecycle" {
  bucket = aws_s3_bucket.frontend_web.id

  rule {
    id     = "expire-old-versions"
    status = "Enabled"

    noncurrent_version_expiration {
      noncurrent_days = 30
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "frontend_web_encryption" {
  bucket = aws_s3_bucket.frontend_web.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_website_configuration" "frontend_web" {
  bucket = aws_s3_bucket.frontend_web.id

  index_document {
    suffix = "index.html"
  }

  error_document {
    key = "index.html"
  }
}

# 只关闭"公开访问"里跟 bucket policy 相关的两项，ACL 相关的两项保持开启
resource "aws_s3_bucket_public_access_block" "frontend_web_public_access" {
  #checkov:skip=CKV_AWS_54:公开静态网站托管，跟 CKV2_AWS_6 同一个根因
  #checkov:skip=CKV_AWS_56:同上
  bucket                  = aws_s3_bucket.frontend_web.id
  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = false
  restrict_public_buckets = false
}

resource "aws_s3_bucket_policy" "frontend_web_public_read" {
  bucket = aws_s3_bucket.frontend_web.id
  # 必须等 public access block 先放开，否则这条 policy 会被挡下来
  depends_on = [aws_s3_bucket_public_access_block.frontend_web_public_access]

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect    = "Allow"
        Principal = "*"
        Action    = ["s3:GetObject"]
        Resource  = "${aws_s3_bucket.frontend_web.arn}/*"
      }
    ]
  })
}
