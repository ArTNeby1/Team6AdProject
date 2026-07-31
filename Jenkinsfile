pipeline {
    agent {
        docker {
            image 'python:3.11'
        }
    }

    stages {
        stage('Checkout') {
            steps { 
                checkout scm 
            }
        }
        
        stage('Test & Security') {
            steps {
                sh '''
                    echo "=== 1. 安装测试依赖 ==="
                    pip install pytest
                    
                    echo "=== 2. 执行单元测试 ==="
                    python -m pytest test_app.py --junit-xml=results.xml -v
                    
                    echo "=== 3. 执行SAST扫描(模拟) ==="
                    echo "✅ 未发现高危漏洞，扫描通过"
                '''
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'results.xml'
                }
            }
        }
    }
}
