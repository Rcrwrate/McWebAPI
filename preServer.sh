git clone --depth=1 https://cnb.cool/shirokasoke/GTNH /tmp/GTNH
cd /tmp/GTNH
git lfs pull

mv /tmp/GTNH/2.8.4 /workspace/GTNH-server

wget https://cnb.cool/Cool_Sapphire/file/-/releases/download/2.8.4/2.8.4.dumps.7z
7z x 2.8.4.dumps.7z -o/workspace/GTNH-server

rm -rf /tmp/GTNH

