"""
跟 validate_examples.py 做同一件事(校验 example_valid.json / example_invalid.json),
但改用 Pydantic,不再需要单独的 jsonschema 库和 trip_schema.json 文件。
用来证明:字段设计(task 1)完全保留,只是换了个更适合后续接 LangChain 的写法。
"""
import json
import sys
from pathlib import Path

from pydantic import ValidationError

SCHEMA_DIR = Path(__file__).resolve().parent.parent / "schema"
sys.path.insert(0, str(SCHEMA_DIR))  # trip_models.py 放在 ML/schema/ 下，不是标准包结构，先手动加进搜索路径

from trip_models import TripExtraction  # noqa: E402


def load(name):
    return json.loads((SCHEMA_DIR / name).read_text(encoding="utf-8"))


def check(name):
    data = load(name)
    try:
        trip = TripExtraction.model_validate(data)
        print(f"[PASS] {name} 符合 schema (解析出 {len(trip.places)} 个地点)")
    except ValidationError as e:
        first_error = e.errors()[0]
        print(f"[FAIL] {name} 不符合 schema -> {first_error['msg']} (位置: {first_error['loc']})")


if __name__ == "__main__":
    check("example_valid.json")
    check("example_invalid.json")
