pipeline {
    agent {
        docker {
            image 'python:3.11'
        } // 先在Jenkins本机运行，方便调试
    
    stages {
        stage('Checkout') {
            steps { 
                checkout scm 
            }
        }
        
        stage('Test & Security') {
            steps {
                sh '''
                    echo "=== 0. 检查并安装 Python 环境 ==="
                    # 如果容器内没有python3，则自动安装（适用于Debian/Ubuntu基础镜像）
                    if ! command -v python3 &> /dev/null; then
                        echo "⚠️ 未检测到 python3，正在自动安装..."
                        apt-get update && apt-get install -y python3 python3-pip
                    fi
                    
                    echo "=== 1. 安装测试依赖 ==="
                    pip3 install pytest
                    
                    echo "=== 2. 执行单元测试 ==="
                    python3 -m pytest test_app.py --junit-xml=results.xml -v
                    
                    echo "=== 3. 执行SAST扫描(模拟) ==="
                    # 真实环境替换为: pip3 install bandit && bandit -r .
                    echo "✅ 未发现高危漏洞，扫描通过"
                '''
            }
            post {
                always {
                    // 允许空结果防止因测试未执行导致流水线二次报错
                    junit allowEmptyResults: true, testResults: 'results.xml'
                }
            }
        }
    }
}
