bash JetBrains.sh

/root/.vscode-server/bin/$VSCODE_COMMIT_ID/bin/code-server --server-data-dir /root/.vscode-server --telemetry-level all \
    --install-extension vscjava.vscode-gradle \
    --install-extension vscjava.vscode-java-pack 
    # --install-extension georgewfraser.vscode-javac

cd /workspace/sdk/ts && npm i
npm i typescript bun -g
npm i tsx -g

cd /workspace
bash CF.sh