"""可选的 MySQL 元数据存储，稿件与参考资料仍保存在文件目录。"""

import json
from typing import Any
from urllib.parse import unquote, urlsplit


class MySQLRecords:
    def __init__(self, url: str):
        import pymysql

        parsed = urlsplit(url)
        database = parsed.path.strip("/")
        if parsed.scheme != "mysql" or not parsed.hostname or not database:
            raise ValueError("WORKBENCH_MYSQL_URL 应为 mysql://用户:密码@主机:端口/库名")
        self.options = {
            "host": parsed.hostname,
            "port": parsed.port or 3306,
            "user": unquote(parsed.username or ""),
            "password": unquote(parsed.password or ""),
            "database": unquote(database),
            "charset": "utf8mb4",
            "autocommit": True,
        }
        self.connect = pymysql.connect
        self.execute(
            """CREATE TABLE IF NOT EXISTS workbench_manuscripts (
                   article_id VARCHAR(48) PRIMARY KEY,
                   payload JSON NOT NULL
               ) ENGINE=InnoDB""",
        )

    def execute(self, query: str, params: tuple = ()) -> tuple:
        with self.connect(**self.options) as connection, connection.cursor() as cursor:
            cursor.execute(query, params)
            return cursor.fetchall() if cursor.description else ()

    def save(self, article_id: str, record: dict[str, Any]) -> None:
        payload = json.dumps(record, ensure_ascii=False)
        self.execute(
            """INSERT INTO workbench_manuscripts (article_id, payload) VALUES (%s, %s)
               ON DUPLICATE KEY UPDATE payload = %s""",
            (article_id, payload, payload),
        )

    def load(self, article_id: str) -> dict[str, Any] | None:
        rows = self.execute("SELECT payload FROM workbench_manuscripts WHERE article_id = %s", (article_id,))
        return json.loads(rows[0][0]) if rows else None

    def list_all(self) -> list[dict[str, Any]]:
        return [json.loads(row[0]) for row in self.execute("SELECT payload FROM workbench_manuscripts")]

    def delete(self, article_id: str) -> None:
        self.execute("DELETE FROM workbench_manuscripts WHERE article_id = %s", (article_id,))
