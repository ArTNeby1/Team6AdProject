# Team6AdProject
1、本地部署jenkins，碰到jenkins版本过低无法安装插件。在网页中下载最新的war包，并找到镜像中的war包地址，使用docker cp命令进行替换，并更新yml文件中的jdk版本为支持当前jenkins的版本；成功后，添加pipeline\pipeline stage view\Git\Docker Pipeline\Junit\timestamper\pipeline utility steps\warnings 插件
2、本地部署中，遇到pipeline自动构建时缺少python环境,修改yml，添加python镜像并开启Docker Pipeline插件
