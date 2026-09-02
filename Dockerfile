FROM docker.cnb.cool/shirokasoke/env/pure

RUN /root/.vscode-server/bin/$VSCODE_COMMIT_ID/bin/code-server --server-data-dir /root/.vscode-server --telemetry-level all \
    --install-extension vscjava.vscode-gradle \
    --install-extension vscjava.vscode-java-pack 

ARG CNB_REPO_SLUG
ENV CNB_REPO_SLUG=${CNB_REPO_SLUG}
ENV maven_TOKEN=""

RUN apt install -y gnupg ca-certificates curl && \
    curl -s https://repos.azul.com/azul-repo.key \
    | sudo gpg --dearmor -o /usr/share/keyrings/azul.gpg && \
    echo "deb [signed-by=/usr/share/keyrings/azul.gpg] https://repos.azul.com/zulu/deb stable main" \
    | sudo tee /etc/apt/sources.list.d/zulu.list && \
    apt update && \
    apt install zulu8-jdk zulu17-jdk zulu21-jdk zulu25-jdk telnet -y && \
    apt clean && \
    rm -rf /var/lib/apt/lists/*

RUN cd /bin && curl -L https://arthas.aliyun.com/install.sh | sh

RUN GIT_LFS_SKIP_SMUDGE=1 git clone --depth=1 https://cnb.cool/${CNB_REPO_SLUG} /tmp/repo &&\
    cd /tmp/repo &&\
    ./gradlew setupDecompWorkspace

RUN cd /tmp/repo &&\
    ./gradlew injectTags spotlessApply build && rm -rf /tmp/repo/build/libs || true
