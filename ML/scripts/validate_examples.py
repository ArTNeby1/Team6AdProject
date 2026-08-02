"""
用 trip_schema.json 校验 example_valid.json / example_invalid.json。
这是 Sprint 1 spike 脚本的第一块积木：先证明"校验"这一步能跑通，
后面任务 3 只需要把 "读文件里的 JSON" 换成 "模型返回的 JSON" 即可。
"""
import json
from pathlib import Path

from jsonschema import validate, ValidationError

SCHEMA_DIR = Path(__file__).resolve().parent.parent / "schema"


def load(name):
    return json.loads((SCHEMA_DIR / name).read_text(encoding="utf-8"))


def check(name, schema):
    data = load(name)
    try:
        validate(instance=data, schema=schema)
        print(f"[PASS] {name} 符合 schema")
    except ValidationError as e:
        print(f"[FAIL] {name} 不符合 schema -> {e.message}  (出错位置: {list(e.path)})")


if __name__ == "__main__":
    schema = load("trip_schema.json")
    check("example_valid.json", schema)
    check("example_invalid.json", schema)
