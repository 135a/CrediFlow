FROM python:3.10-slim

WORKDIR /app

# 设置国内镜像源
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple

COPY . .

ENV TZ=Asia/Shanghai
ENV ACTIVE_PROVIDER=qwen
ENV MILVUS_HOST=milvus
ENV JAVA_SERVICE_BASE=http://apisix:9080

EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
