# 经 DaoCloud 拉取，避免 docker.io 走失效的 ustc 等 mirror 导致 EOF
FROM docker.m.daocloud.io/library/python:3.10-slim
WORKDIR /app
ENV TZ=Asia/Shanghai
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY *.py ./
ENV ACTIVE_PROVIDER=qwen
ENV MILVUS_HOST=milvus
ENV JAVA_SERVICE_BASE=http://apisix:9080
EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
