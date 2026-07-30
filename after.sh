bash JetBrains.sh

/root/.vscode-server/bin/$VSCODE_COMMIT_ID/bin/code-server --server-data-dir /root/.vscode-server --telemetry-level all \
    --install-extension vscjava.vscode-gradle \
    --install-extension vscjava.vscode-java-pack 
    # --install-extension georgewfraser.vscode-javac

cd /workspace/sdk/ts
npm i
npm i typescript bun -g
npm i tsx -g

cd /workspace/web
bun i
# bun i @shirokasoke/webapi-sdk --registry=https://npm.cnb.cool/shirokasoke/npm/-/packages/
sed -i 's|../../../src/main/java/love/shirokasoke|/workspace/src/main/java/love/shirokasoke|g' /workspace/web/node_modules/@shirokasoke/webapi-sdk/dist/client.d.ts

cd /workspace
bash CF.sh