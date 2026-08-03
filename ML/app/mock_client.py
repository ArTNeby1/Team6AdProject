"""
Mock 模型客户端：不真的联网调用 Bedrock。

真实的 AI 模型返回的是一段"文字"（有可能这段文字本身格式就是错的，
或者内容缺字段、瞎编数据）。所以这里所有函数都返回 str（文字），
不是已经处理好的 dict —— 这样才能真实模拟"AI 有时候不听话"的各种情况，
调用方(main.py)必须自己做 json.loads 解析 + schema 校验这两道关卡。

函数签名（参数、返回值的样子）故意跟以后真实模型客户端保持一致：
输入一段文本，输出一段文字。以后额度解锁了，只需要把"调用这个函数"的地方
换成"调用真实模型的函数"，调用方的代码不用改。

mock_extract          -- 正常情况：返回写死的、完全合规的 JSON 文字
mock_extract_bad_json  -- 坏情况1：返回半截、格式错误的 JSON 文字
mock_extract_missing_field -- 坏情况2：JSON 格式是对的，但漏了必填字段 name
mock_extract_bad_coords    -- 坏情况3：JSON 格式是对的，但坐标是瞎编的、类型也不对
"""
import json
import sys
from pathlib import Path

SCHEMA_DIR = Path(__file__).resolve().parent.parent / "schema"
sys.path.insert(0, str(SCHEMA_DIR))  # trip_models.py 不在标准包路径下，手动加进搜索路径

from trip_models import TripExtraction  # noqa: E402


def mock_extract(text: str, source_name: str = "mock") -> str:
    """
    假装"读了 text 之后抽取出结构化数据"，实际上不看 text 内容，
    直接返回写死的、完全合规的 JSON 文字。
    source_name: 这份数据算是来自哪份输入文本，写进每个 place 的 source 字段。
    """
    data = {
        "destination": "Singapore",
        "dates": ["2025-08-12", "2025-08-13"],
        "places": [
            {
                "name": "Gardens by the Bay",
                "type": "attraction",
                "coords": None,
                "activities": ["Cloud Forest dome", "Super Tree Grove light show"],
            },
            {
                "name": "Satay by the Bay",
                "type": "restaurant",
                "coords": None,
                "activities": ["dinner"],
            },
        ],
    }
    return json.dumps(data, ensure_ascii=False)


def mock_extract_bad_json(text: str, source_name: str = "mock") -> str:
    """坏情况1：模拟模型没写完就断了，返回的文字根本不是合法 JSON。"""
    return '{"destination": "Singapore", "places": ['


def mock_extract_missing_field(text: str, source_name: str = "mock") -> str:
    """坏情况2：JSON 格式没错，但 place 里漏了必填字段 name。"""
    data = {
        "destination": "Singapore",
        "places": [
            {"type": "attraction"}
        ],
    }
    return json.dumps(data, ensure_ascii=False)


def mock_extract_bad_coords(text: str, source_name: str = "mock") -> str:
    """坏情况3：JSON 格式没错，但坐标是模型瞎编的，而且连类型都不对(lat应该是数字)。"""
    data = {
        "destination": "Singapore",
        "places": [
            {
                "name": "Gardens by the Bay",
                "type": "attraction",
                "coords": {"lat": "大概在北边", "lng": 103.8198},
            }
        ],
    }
    return json.dumps(data, ensure_ascii=False)


if __name__ == "__main__":
    # 自我检查：证明 mock_extract 返回的东西真的能通过 Pydantic 校验，不是空口说白话
    raw = mock_extract("随便什么文本，这里不会真的被读取", source_name="test.txt")
    trip = TripExtraction.model_validate(json.loads(raw))
    print(f"[PASS] mock_extract 返回结果符合 schema，解析出 {len(trip.places)} 个地点")
