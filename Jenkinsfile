pipeline {
    agent any // 先在Jenkins本机运行，方便调试
    stages {
        stage('Checkout') {
            steps {
                checkout scm // 拉取你的代码
            }
        }
        stage('Test & Security') {
            steps {
                // 这里用sh模拟了单元测试和SAST扫描步骤
                sh '''
                    echo "1. 开始执行单元测试..."
                    python3 -m pytest test_app.py --junit-xml=results.xml
                    
                    echo "2. 开始执行SAST扫描(模拟)..."
                    # 真实环境会调用 safety check 或 bandit
                    echo "未发现高危漏洞，扫描通过"
                '''
            }
            post {
                always {
                    // 无论成功失败，都收集测试报告
                    junit 'results.xml'
                }
            }
        }
    }
}
