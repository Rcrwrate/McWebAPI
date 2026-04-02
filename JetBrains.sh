# 使用 SDKMAN 安装 JetBrains Runtime
# curl -s "https://get.sdkman.io" | bash
# source "$HOME/.sdkman/bin/sdkman-init.sh"

# 安装 JetBrains JDK
# sdk install java 21.0.10-jbr 
# sdk install java 25.0.2-jbr

mkdir -p /usr/lib/jvm/jbr25
cd /workspace/tools
tar -zxvf jbrsdk_jcef-25.0.2-linux-x64-b300.57.tar.gz
mv jbrsdk_jcef-25.0.2-linux-x64-b300.57/* /usr/lib/jvm/jbr25/
rm -rf /workspace/tools/jbrsdk_jcef-25.0.2-linux-x64-b300.57/