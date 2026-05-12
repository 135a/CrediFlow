from pymilvus import connections, FieldSchema, CollectionSchema, DataType, Collection, utility
from config import Config

class MilvusManager:
    def __init__(self):
        self.host = Config.MILVUS_HOST
        self.port = Config.MILVUS_PORT
        self.collection_name = "crediflow_knowledge"
        self._connect()

    def _connect(self):
        try:
            connections.connect("default", host=self.host, port=self.port)
        except Exception as e:
            print(f"Failed to connect to Milvus: {e}")

    def create_collection_if_not_exists(self):
        if utility.has_collection(self.collection_name):
            return Collection(self.collection_name)

        fields = [
            FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=True),
            FieldSchema(name="source_id", dtype=DataType.VARCHAR, max_length=200),
            FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=768),
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
