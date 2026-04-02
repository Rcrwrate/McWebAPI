# sudo apt install -y gnupg ca-certificates curl

# curl -s https://repos.azul.com/azul-repo.key \
#   | sudo gpg --dearmor -o /usr/share/keyrings/azul.gpg

# echo "deb [signed-by=/usr/share/keyrings/azul.gpg] https://repos.azul.com/zulu/deb stable main" \
#   | sudo tee /etc/apt/sources.list.d/zulu.list


# # wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | tee /usr/share/keyrings/adoptium.asc
# # echo "deb [signed-by=/usr/share/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | tee /etc/apt/sources.list.d/adoptium.list

# apt update

# apt install zulu8-jdk zulu17-jdk zulu21-jdk zulu25-jdk -y

# # apt install temurin-8-jdk temurin-17-jdk temurin-21-jdk temurin-25-jdk -y

# ./gradlew setupDecompWorkspace

# ./gradlew injectTags

echo "" >> /etc/profile
echo "alias s='./gradlew spotlessApply'" >> /etc/profile
echo "alias b='./gradlew build'" >> /etc/profile
echo "alias run='./gradlew runServer25'" >> /etc/profile

mv /tmp/repo/.gradle ./.gradle
mkdir -p ./build
mv /tmp/repo/build/* ./build
mkdir -p ./run/natives
mv /tmp/repo/run/natives/* ./run/natives

