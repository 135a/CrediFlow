from pymilvus import connections, FieldSchema, CollectionSchema, DataType, Collection, utility
from config import Config

class MilvusManager:
    def __init__(self):
        self.host = Config.MILVUS_HOST
        self.port = Config.MILVUS_PORT
        self.collection_name = "crediflow_knowledge"
        self._connect()

    def _connect(self):
        import time
        max_retries = 10
        for i in range(max_retries):
            try:
                connections.connect("default", host=self.host, port=self.port)
                print("Successfully connected to Milvus.")
                return
            except Exception as e:
                print(f"Failed to connect to Milvus (attempt {i+1}/{max_retries}): {e}")
                time.sleep(3)
        raise ConnectionError("Could not connect to Milvus after multiple retries.")

    def create_collection_if_not_exists(self):
        if utility.has_collection(self.collection_name):
            collection = Collection(self.collection_name)
            # 校验已存在的 Schema 维度是否与当前配置一致
            for field in collection.schema.fields:
                if field.name == "embedding":
                    if field.params.get("dim") != Config.EMBEDDING_DIMENSION:
                        raise ValueError(
                            f"Milvus Schema dimension mismatch! "
                            f"Existing dimension is {field.params.get('dim')}, "
                            f"but current Config.EMBEDDING_DIMENSION is {Config.EMBEDDING_DIMENSION}. "
                            f"Please drop the collection '{self.collection_name}' manually and re-ingest data."
                        )
            return collection

        fields = [
            FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=True),
            FieldSchema(name="source_id", dtype=DataType.VARCHAR, max_length=200),
            FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=Config.EMBEDDING_DIMENSION),
            FieldSchema(name="content", dtype=DataType.VARCHAR, max_length=2000)
        ]
        schema = CollectionSchema(fields, "Knowledge Base for CrediFlow RAG")
        collection = Collection(self.collection_name, schema)
        
        # Create Index
        index_params = {
            "metric_type": "L2",
            "index_type": "IVF_FLAT",
            "params": {"nlist": 1024}
        }
        collection.create_index("embedding", index_params)
        return collection

    def insert(self, source_id: str, content: str, embedding: list):
        collection = Collection(self.collection_name)
        data = [
            [source_id],
            [embedding],
            [content]
        ]
        collection.insert(data)
