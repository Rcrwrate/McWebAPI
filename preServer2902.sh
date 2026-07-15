git clone --depth=1 https://cnb.cool/shirokasoke/GTNH /tmp/GTNH
cd /tmp/GTNH
git lfs pull

mkdir /tmp/2.9.0
cd /tmp/2.9.0
wget https://cnb.cool/Cool_Sapphire/file/-/releases/download/2.9.0/GT_New_Horizons_2.9.0-beta-2_Server_Java_17-25.zip
unzip GT_New_Horizons_2.9.0-beta-2_Server_Java_17-25.zip
rm GT_New_Horizons_2.9.0-beta-2_Server_Java_17-25.zip

wget https://github.com/reobf/Programmable-Hatches-Mod/releases/download/v0.2.0p6-beta/programmablehatches-0.2.0p6.jar
mv programmablehatches-0.2.0p6.jar mods/

cp -r /tmp/GTNH/2.8.4/World/ ./World/
echo '/usr/lib/jvm/zulu25-ca-amd64/bin/java -Xms6G -Xmx32G -Dmixin.debug.export=true -Dfml.queryResult=confirm -Dfml.readTimeout=180 @java9args.txt -jar lwjgl3ify-forgePatches.jar nogui' > startserver-java9.sh

cp /tmp/GTNH/2.8.4/eula.txt ./eula.txt
cp -r /tmp/2.9.0 /workspace/GTNH-server
rm -rf /tmp/2.9.0
rm -rf /tmp/GTNH