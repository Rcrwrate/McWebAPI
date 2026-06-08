import requests

API_KEY = "sk-PWISlpOkJqoCTqNHoi5RXBIFQojVGwd3Nxh7pV8UZVFSINpO"  # 替换为你的 OpenAI API Key
URL = "https://op.aancn.cn/v1/models"

headers = {
    "Authorization": f"Bearer {API_KEY}",
}

try:
    response = requests.get(URL, headers=headers, timeout=15)
    response.raise_for_status()  # 如果状态码不是 2xx，会抛出异常

    data = response.json()
    models = data.get("data", [])

    if not models:
        print("未找到任何模型，请检查 API Key 权限。")
    else:
        print(f"共找到 {len(models)} 个模型：")
        for model in models:
            print(f"- {model['id']}")

except requests.exceptions.RequestException as e:
    print(f"请求出错: {e}")
except ValueError as e:
    print(f"响应解析失败: {e}")