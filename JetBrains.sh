# 使用 SDKMAN 安装 JetBrains Runtime
# curl -s "https://get.sdkman.io" | bash
# source "$HOME/.sdkman/bin/sdkman-init.sh"

# 安装 JetBrains JDK
# sdk install java 21.0.10-jbr 
# sdk install java 25.0.2-jbr

wget https://cnb.cool/Cool_Sapphire/file/-/lfs/5384d9e1e6f8b155bb96ebb36e762e2a84cb0bf48a52d86a429f253d8370bbdd?name=jbrsdk_jcef-25.0.2-linux-x64-b300.57.tar.gz -O jbrsdk_jcef-25.0.2-linux-x64-b300.57.tar.gz

tar -zxvf jbrsdk_jcef-25.0.2-linux-x64-b300.57.tar.gz
mkdir -p /usr/lib/jvm/jbr25/
mv jbrsdk_jcef-25.0.2-linux-x64-b300.57/* /usr/lib/jvm/jbr25/
rm -rf /workspace/jbrsdk_jcef-25.0.2-linux-x64-b300.57/
rm -rf /workspace/jbrsdk_jcef-25.0.2-linux-x64-b300.57.tar.gz