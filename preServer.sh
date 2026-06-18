git clone --depth=1 https://cnb.cool/shirokasoke/GTNH /tmp/GTNH
cd /tmp/GTNH
git lfs pull

mv /tmp/GTNH/2.8.4 /workspace/GTNH-server

wget https://cnb.cool/Cool_Sapphire/file/-/releases/download/2.8.4/2.8.4.dumps.6.18.7z
7z x 2.8.4.dumps.6.18.7z -o/workspace/GTNH-server

rm -rf /tmp/GTNH

# cat ./GTNH-server/logs/fml-junk-earlystartup.log | grep -oE '[0-9]+ms' | sort | uniq -c | sort -rn