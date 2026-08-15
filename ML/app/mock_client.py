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
import re
import sys
from datetime import date, timedelta
from pathlib import Path

SCHEMA_DIR = Path(__file__).resolve().parent.parent / "schema"
sys.path.insert(0, str(SCHEMA_DIR))  # trip_models.py 不在标准包路径下，手动加进搜索路径

from trip_models import TripExtraction  # noqa: E402


def mock_duration_days(text: str) -> int | None:
    """
    **Mock 专用**的"从文本里看出玩几天"，正则实现。

    ⚠️ 真实链路（bedrock / ollama）**不走这里** —— 那边是模型按
    `trip_models.TripExtraction.duration_days` 的 description 抽出来的。
    这个函数存在的唯一理由：让 mock 模式能同时演示两条分支。以前 mock 恒定
    返回 `duration_days: 2`，等于"没抽到天数 -> 前端弹窗问用户"那条路在 mock
    下**永远测不到**，而那恰恰是前后端最需要联调的一条。

    认三种写法，都超出 1~30 就当没抽到（跟真实链路"宁可为空也不编"一致）：
      "3 days" / "3-day"      -> 3
      "3天"                    -> 3
      "Day 1 ... Day 3" 这种小标题 -> 3（取最大的那个）
    什么都没写 -> None
    """
    lowered = text.lower()

    def _in_range(value: int) -> int | None:
        return value if 1 <= value <= 30 else None

    match = re.search(r"(\d+)\s*-?\s*days?\b", lowered) or re.search(r"(\d+)\s*天", lowered)
    if match:
        return _in_range(int(match.group(1)))

    # "Day 1 / Day 2 / Day 3" 这种按天分段的正文：天数 = 最大的那个编号。
    # 注意这条要放在上面那条后面 —— "3 days" 里的 day 后面没有数字，不会误命中。
    day_headings = [int(d) for d in re.findall(r"\bday\s*(\d+)", lowered)]
    if day_headings:
        return _in_range(max(day_headings))

    return None


def mock_extract(text: str, source_name: str = "mock") -> str:
    """
    假装"读了 text 之后抽取出结构化数据"。地点数据是写死的（不看 text），
    日期按调用时的当天动态生成（今天起 30 天后连续两天），避免 demo 里日期
    停在某个已经过去的固定值。
    source_name: 这份数据算是来自哪份输入文本，写进每个 place 的 source 字段。

    **只有 `duration_days` 会真的看 text**（见 mock_duration_days 的说明）：
    输入里写了"3 days"/"3天"/"Day 1..Day 3"就返回对应天数，没写就是 null。
    所以后端/前端拿 mock 联调时，两条分支各自怎么测：
      "a 3-day trip in Singapore" -> duration_days=3, needs_duration_input=false
      "I want to see Gardens by the Bay" -> duration_days=null, needs_duration_input=true
    """
    start = date.today() + timedelta(days=30)
    end = start + timedelta(days=1)
    data = {
        "destination": "Singapore",
        "dates": [start.isoformat(), end.isoformat()],
        # 故意**不**跟上面两个日期联动：dates 有两个日期不代表"玩2天"，这正是
        # duration_days 单独存在的理由（见 trip_models.py 里那段注释）。
        "duration_days": mock_duration_days(text),
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


def mock_extract_no_content(text: str, source_name: str = "mock") -> str:
    """
    坏情况4（其实是"好情况"，模型没犯错）：模拟文本里压根没有可提取的旅行信息——
    模型老老实实回 destination/places 全空，不编。用来测 orchestrator 把这种
    结果识别成 NO_USEFUL_CONTENT 而不是 ExtractionFailedError 那条分支。
    真实链路不会主动挑这个函数用：这是给单元测试直接注入 EXTRACT_FN 用的，
    HTTP 层的 mock_extract() 不看输入内容、恒定返回两条假地点，跟其它字段的
    mock 限制一致（见 main.py 顶部 mock 模式说明），要测这条分支只能靠这个
    函数或真实模型。
    """
    data = {"destination": "", "places": []}
    return json.dumps(data, ensure_ascii=False)


def mock_extract_bad_coords(text: str, source_name: str = "mock") -> str:
    """坏情况3：JSON 格式没错，但坐标是模型瞎编的，而且连类型都不对(lat应该是数字)。"""
    data = {
        "destination": "Singapore",
        "places": [
            {
                "name": "Gardens by the Bay",
                "type": "attraction",
                "coords": {"lat": "somewhere up north", "lng": 103.8198},
            }
        ],
    }
    return json.dumps(data, ensure_ascii=False)


if __name__ == "__main__":
    # 自我检查：证明 mock_extract 返回的东西真的能通过 Pydantic 校验，不是空口说白话
    raw = mock_extract("any text, the places are never actually read", source_name="test.txt")
    trip = TripExtraction.model_validate(json.loads(raw))
    print(f"[PASS] mock_extract output matches schema, parsed {len(trip.places)} places")

    # 再证明 duration_days 那条分支两边都通。
    # 示例文本一律用英文：这些字符串直接打印到 Windows 控制台，中文在默认代码页下
    # 是乱码，看不出测的是哪条用例（`天` 的正则分支本身仍然保留在上面的实现里）。
    for sample, expected in [
        ("a 3-day trip in Singapore", 3),
        ("a 5 day holiday in Singapore", 5),
        ("Day 1: Gardens by the Bay. Day 2: Sentosa. Day 3: Jewel.", 3),
        ("I want to see Gardens by the Bay", None),
        ("a 999-day trip", None),  # 越界当没抽到，不编
    ]:
        got = TripExtraction.model_validate(json.loads(mock_extract(sample))).duration_days
        tag = "PASS" if got == expected else "FAIL"
        print(f"[{tag}] duration_days({sample!r}) -> {got!r} (expected {expected!r})")
