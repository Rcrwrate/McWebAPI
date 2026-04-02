FROM docker.cnb.cool/shirokasoke/env/pure

ARG CNB_REPO_SLUG
ENV CNB_REPO_SLUG=${CNB_REPO_SLUG}
ENV maven_TOKEN=""

RUN sudo apt install -y gnupg ca-certificates curl && \
    curl -s https://repos.azul.com/azul-repo.key \
    | sudo gpg --dearmor -o /usr/share/keyrings/azul.gpg && \
    echo "deb [signed-by=/usr/share/keyrings/azul.gpg] https://repos.azul.com/zulu/deb stable main" \
    | sudo tee /etc/apt/sources.list.d/zulu.list && \
    apt update && \
    apt install zulu8-jdk zulu17-jdk zulu21-jdk zulu25-jdk -y && \
    apt clean && \
    rm -rf /var/lib/apt/lists/*

RUN GIT_LFS_SKIP_SMUDGE=1 git clone --depth=1 https://cnb.cool/${CNB_REPO_SLUG} /tmp/repo &&\
    cd /tmp/repo &&\
    ./gradlew setupDecompWorkspace injectTags -Dhttps.protocols=TLSv1,TLSv1.1,TLSv1.2,TLSv1.3
