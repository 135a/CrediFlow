from sqlalchemy import create_engine, text
from config import Config

# 白名单表
ALLOWED_TABLES = {"cf_user", "cf_credit_result", "cf_loan_application"}

class NL2SQLEngine:
    def __init__(self):
        self.engine = create_engine(Config.DB_URI_RO)

    def is_safe_query(self, sql: str) -> bool:
        sql_lower = sql.lower()
        # 拦截写操作
        if any(keyword in sql_lower for keyword in ["insert", "update", "delete", "drop", "alter", "truncate"]):
            return False
        # 简单表白名单校验
        for table in ALLOWED_TABLES:
            if table in sql_lower:
                return True
        return False

    def execute(self, sql: str, limit: int = 50):
        if not self.is_safe_query(sql):
            return {"error": "Unsafe query or unauthorized table access."}
        
        # 强制增加 LIMIT
        if "limit" not in sql.lower():
            sql += f" LIMIT {limit}"

        try:
            with self.engine.connect() as conn:
                result = conn.execute(text(sql))
                rows = [dict(row._mapping) for row in result]
                
                # 数据掩码处理 (例如掩码身份证/手机号)
                for row in rows:
                    if "phone" in row and row["phone"]:
                        row["phone"] = row["phone"][:3] + "****" + row["phone"][-4:]
                    if "id_card" in row and row["id_card"]:
                        row["id_card"] = row["id_card"][:6] + "********" + row["id_card"][-4:]
                        
                return rows
        except Exception as e:
            return {"error": str(e)}
