bash CF.sh
bash JetBrains.sh

vsfix
/root/.vscode-server/bin/$VSCODE_COMMIT_ID/bin/code-server --server-data-dir /root/.vscode-server --telemetry-level all \
    --install-extension vscjava.vscode-gradle \
    --install-extension vscjava.vscode-java-pack 
    # --install-extension georgewfraser.vscode-javac
