set -e

./gradlew spotlessApply

rm -rf /workspace/build/libs

./gradlew build

target=$(ls /workspace/build/libs/ | grep -vE "(dev|sources|preshadow).jar")

ls /workspace/GTNH-server/mods/ | grep webapi | while read line
do
    echo "$line"
    rm "/workspace/GTNH-server/mods/$line"
done

cp "/workspace/build/libs/$target" "/workspace/GTNH-server/mods/$target"

cd /workspace/GTNH-server
bash startserver-java9.sh

