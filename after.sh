bash CF.sh
bash JetBrains.sh

commit_id=$(ls /root/.vscode-server/bin)

/root/.vscode-server/bin/$commit_id/bin/code-server --server-data-dir /root/.vscode-server --telemetry-level all \
    --install-extension vscjava.vscode-gradle \
    --install-extension vscjava.vscode-java-pack 
    # --install-extension georgewfraser.vscode-javac
