import requests
import time
import os

API_KEY = "sk-2db9dd2327b4425ab10335e934f8e87a"
URL = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding"

def test_qwen_api():
    print(f"正在测试阿里云千问 API 连接...")
    print(f"使用的 API_KEY: {API_KEY[:6]}...{API_KEY[-4:]}")
    print(f"请求地址: {URL}")
    print("-" * 50)
    
    headers = {
        "Authorization": f"Bearer {API_KEY}",
        "Content-Type": "application/json"
    }
    
    data = {
        "model": "text-embedding-v1",
        "input": {
            "texts": ["测试阿里云连通性"]
        }
    }
    
    start_time = time.time()
    try:
        # 设置超时时间为 15 秒，观察是否依然超时
        response = requests.post(URL, headers=headers, json=data, timeout=15)
        elapsed_time = time.time() - start_time
        
        print(f"耗时: {elapsed_time:.2f} 秒")
        print(f"HTTP 状态码: {response.status_code}")
        
        if response.status_code == 200:
            print("✅ 成功！网络连接正常，且 API Key 有效。")
            print("返回数据示例:", str(response.json())[:100], "...")
        elif response.status_code in [400, 401, 403]:
            print("❌ 失败！网络连接正常，但 API Key 无效或请求参数有误。")
            print("错误信息:", response.text)
        else:
            print("⚠️ 未知状态！")
            print("返回数据:", response.text)
            
    except requests.exceptions.ReadTimeout:
        elapsed_time = time.time() - start_time
        print(f"❌ 失败！请求在 {elapsed_time:.2f} 秒后超时。")
        print("结论：这不是 API Key 的问题，而是你的电脑无法与阿里云服务器建立网络连接（网络被墙、DNS错误或代理拦截）。")
    except requests.exceptions.ConnectionError:
        print("❌ 失败！连接被拒绝。")
        print("结论：你的网络可能存在代理配置问题，或者 DNS 无法解析 dashscope.aliyuncs.com。")
    except Exception as e:
        print(f"❌ 发生其他异常: {str(e)}")

def test_zhipu_api():
    ZHIPU_API_KEY = os.getenv("ZHIPU_API_KEY", "")
    if not ZHIPU_API_KEY:
        print("\n⚠️ 未配置环境变量 ZHIPU_API_KEY，跳过智谱 API 测试。")
        print("提示：可以通过 set ZHIPU_API_KEY=xxx 来配置并测试。")
        return
        
    ZHIPU_URL = "https://open.bigmodel.cn/api/paas/v4/embeddings"
    print(f"\n正在测试智谱 API 连接...")
    print(f"使用的 API_KEY: {ZHIPU_API_KEY[:6]}...{ZHIPU_API_KEY[-4:] if len(ZHIPU_API_KEY)>10 else ''}")
    print(f"请求地址: {ZHIPU_URL}")
    print("-" * 50)
    
    headers = {
        "Authorization": f"Bearer {ZHIPU_API_KEY}",
        "Content-Type": "application/json"
    }
    
    data = {
        "model": "embedding-2",
        "input": "测试智谱连通性"
    }
    
    start_time = time.time()
    try:
        response = requests.post(ZHIPU_URL, headers=headers, json=data, timeout=15)
        elapsed_time = time.time() - start_time
        
        print(f"耗时: {elapsed_time:.2f} 秒")
        print(f"HTTP 状态码: {response.status_code}")
        
        if response.status_code == 200:
            print("✅ 成功！网络连接正常，且 API Key 有效。")
        else:
            print("❌ 失败！错误信息:", response.text)
    except Exception as e:
        print(f"❌ 发生异常: {str(e)}")

if __name__ == "__main__":
    test_qwen_api()
    test_zhipu_api()
