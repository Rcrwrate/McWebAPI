if [ -z "$CF_TOKEN" ]; then
    echo "环境变量 CF_TOKEN 不存在或为空"
else
    echo "环境变量 CF_TOKEN 存在"
    cf
    sudo cloudflared service install "$CF_TOKEN"
fi