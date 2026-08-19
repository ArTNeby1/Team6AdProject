"""
"解析+校验+重试"这套防御逻辑，从 main.py 抽出来放这里。

原因：orchestrator.py（多 agent 编排）和 main.py（FastAPI 路由）都要用同一套
重试逻辑，如果留在 main.py 里，orchestrator.py import main 会跟 main import
orchestrator 形成循环依赖。抽成独立模块，两边各自 import 这里就没有这个问题。
"""
import json
import re
import sys
from datetime import date
from pathlib import Path
from typing import Callable

from pydantic import ValidationError

SCHEMA_DIR = Path(__file__).resolve().parent.parent / "schema"
sys.path.insert(0, str(SCHEMA_DIR))  # trip_models.py 不在标准包路径下，手动加进搜索路径

from trip_models import (  # noqa: E402
    MAX_DURATION_DAYS,
    MIN_DURATION_DAYS,
    TripExtraction,
)

MAX_ATTEMPTS = 3


def _null_unusable_duration(data: dict) -> dict:
    """
    把"抽出来了但没法用"的 duration_days 就地改成 None，在 schema 校验之前。

    为什么要有这一步（实测出来的坑，不是假想）：用户输入 "I am doing a 200-day trip"
    时，模型忠实地抽出 `duration_days: 200`——模型没做错，文本确实这么写的。但 200
    超出 schema 的 1~30，于是 model_validate 抛 ValidationError，extract_with_retry
    重试 3 次（每次都是一次真实的 Bedrock 调用），模型每次都照实再答 200，最后整条
    请求 502。**代价是整个抽取结果全丢**——destination、places、activities 一起没了，
    只因为其中一个可选字段的数字太大。用户看到的是"AI 挂了"，而不是"天数不合适"。

    重试在这里本来就注定失败：重试能救的是"模型输出格式错了"（少字段、type 写错
    枚举值），再问一次有机会改对；而这里模型输出完全正确，是**文本本身**说了个我们
    不支持的天数，问一百次答案还是 200。

    所以改成：天数没法用 -> 当作"没抽到"（None），其余字段照常返回。下游行为自动
    就是对的 —— `needs_duration_input` 恒等于 `duration_days is None`，于是前端弹窗
    问用户玩几天，用户答一个 1~30 的数就继续走。这跟 coords/dates 那套"宁可为空也
    不编"完全一致：与其瞎猜（截断成 30 天？用户可没这么说），不如老老实实问用户。

    只处理这一个字段，不碰其它任何字段：别的字段出问题该重试还是要重试，这里不能
    变成"把所有校验错误都吞掉"的万能补丁。
    """
    if not isinstance(data, dict) or data.get("duration_days") is None:
        return data

    raw = data["duration_days"]
    # bool 要单独挡掉：Python 里 isinstance(True, int) 是 True，不挡的话
    # duration_days: true 会被当成 1 天悄悄放行。
    if isinstance(raw, bool):
        usable = None
    else:
        try:
            # 先转 float 再比对整数：模型偶尔输出 "3"（字符串）或 3.0（浮点），
            # 这两种都是能用的 3；3.5 这种非整数天数则算没法用。
            as_float = float(raw)
            as_int = int(as_float)
            usable = as_int if as_int == as_float else None
        except (TypeError, ValueError):
            # "a week" 这种没法转成数字的文字，同样归到"没抽到"
            usable = None

    if usable is not None and not (MIN_DURATION_DAYS <= usable <= MAX_DURATION_DAYS):
        usable = None

    if usable is None:
        # 打日志而不是静默改掉：这条是"用户明明说了天数、我们却要去问他"的唯一线索，
        # 联调时看不到日志会以为是前端弹窗逻辑出了 bug。
        # 注意只在真的降级时才打——"3"（字符串）会被规范成 3，属于正常放行，
        # 那种情况打一条 "unusable" 会把联调的人往错误方向带。
        print(
            f"[duration] unusable duration_days={raw!r} "
            f"(outside {MIN_DURATION_DAYS}~{MAX_DURATION_DAYS} or not a whole number) "
            f"-> treated as not stated, will ask the user"
        )

    data["duration_days"] = usable
    return data


_YEAR_IN_DATE_RE = re.compile(r"^(\d{4})-(\d{2})-(\d{2})$")


def _fix_invented_dates(data: dict, source_text: str) -> dict:
    """
    把"原文没写年份、模型自己编了一个"的日期，就地改成用当前年份，在 schema 校验之前。

    为什么要有这一步（实测出来的坑，不是假想）：用户输入 "starting February 14th"
    这种没写年份的日期时，schema description 里已经写了"Never invent a year"，但这只是
    prompt 里的提示，Bedrock/Claude 系模型不保证遵守——实测 Nova Lite/Claude Haiku/
    Sonnet 三个候选模型都出现过至少一次编年份（见 ML/docs/model_evaluation.md），
    常见编出来的是训练数据里常见的年份（比如 2023），跟"今天是哪年"完全无关。这条
    日期最终会被前端当成"计划开始日期"展示给用户，编出来的年份会让新生成的计划显示
    成过去的日期。

    这里跟重试无关（不属于 extract_with_retry 那套"重试救得回来"的错误）：原文压根
    没写年份，再问模型一百次它还是只能编，问题不在"没抽对"，而在"这个信息原文里就
    没有"。所以不重试，直接在这一步用确定性规则纠正。

    纠正规则：如果模型抽出的年份能在原文里找到（用户确实写了年份），保留原样；
    找不到就说明是编的，换成当前年份，只留模型抽对的月/日（原文里真实写着的部分）。
    跟 coords/duration_days 那种"没法用就整条清空"不一样：用户明确写了"2月14日"，
    只是编年份不对，直接丢掉整条日期反而丢了原文里真实存在的信息。
    """
    if not isinstance(data, dict) or not data.get("dates"):
        return data

    today = date.today()
    fixed = []
    for raw_date in data["dates"]:
        if not isinstance(raw_date, str):
            continue
        m = _YEAR_IN_DATE_RE.match(raw_date)
        if not m:
            # 格式本身就不对，留给 schema 校验去拒绝/触发重试，这里不处理
            fixed.append(raw_date)
            continue
        year, month, day = m.group(1), m.group(2), m.group(3)
        if year in source_text:
            fixed.append(raw_date)
            continue
        print(
            f"[dates] model invented year {year} (not found in source text) for "
            f"{raw_date} -> replacing with current year {today.year}"
        )
        try:
            fixed.append(date(today.year, int(month), int(day)).isoformat())
        except ValueError:
            # 换成当前年份后日期不合法（比如 2/29 换到非闰年），这条日期没法用，
            # 宁可丢弃也不留一个换算出来仍然错误的值
            continue
    data["dates"] = fixed
    return data


class ExtractionFailedError(Exception):
    """模型连续 MAX_ATTEMPTS 次都没能返回合规结果。"""


def parse_and_validate(raw_text: str, source_text: str = "") -> TripExtraction:
    """
    把模型返回的原始文字，转成校验通过的 TripExtraction 对象。
    要闯两道关：
    1. 格式关：raw_text 本身必须是合法 JSON，不然 json.loads 会抛 JSONDecodeError
    2. 内容关：就算是合法 JSON，字段也不一定齐全/类型不一定对，
       不然 model_validate 会抛 ValidationError
    这两种异常都不在这里处理，直接抛出去，交给调用方(extract_with_retry)决定要不要重试。

    两关之间夹两步：
    - _null_unusable_duration()：天数超范围是**重试救不回来**的一类错误（文本就写着
      200 天，再问几次还是 200），单独降级成"没抽到天数"，免得一个可选字段把整份
      抽取结果拖去 502。理由详见那个函数的说明。
    - _fix_invented_dates(..., source_text)：原文没写年份时模型编的年份，换成当前
      年份。source_text 是原始输入文本（不含重试时附加的 schema 报错），用来判断
      模型抽出的年份是不是原文真实写着的。理由详见那个函数的说明。
    """
    data = json.loads(raw_text)
    data = _null_unusable_duration(data)
    data = _fix_invented_dates(data, source_text)
    return TripExtraction.model_validate(data)


def extract_with_retry(
    text: str,
    source_name: str,
    extract_fn: Callable[..., str],
    max_attempts: int = MAX_ATTEMPTS,
) -> TripExtraction:
    """
    调用 extract_fn(text, source_name=...) 拿结果，闯 parse_and_validate 那两道关；
    闯关失败就重试，最多试 max_attempts 次；每次都失败就抛 ExtractionFailedError，
    由调用方（main.py 转成 502，orchestrator.py 转成 pipeline 的 error 字段）决定怎么处理。
    """
    last_error = None
    for attempt in range(1, max_attempts + 1):
        # 第 2 次起把上一次的校验错误附在输入后面。
        # 之前这里是原样再问一遍，模型没有任何新信息，自然还是给同一个错误答案——
        # 三次重试等于白跑三次。实测例子：llama3 把"国家博物馆"的 type 输出成
        # "museum"（enum 里没有这个值），不告诉它错在哪就会连错三次。
        prompt_text = text
        if last_error is not None:
            prompt_text = (
                f"{text}\n\n"
                f"[SCHEMA VIOLATION IN YOUR PREVIOUS OUTPUT — fix it and output the full JSON again]\n"
                f"{last_error}"
            )

        raw_text = extract_fn(prompt_text, source_name=source_name)
        try:
            # source_text 用原始 text（不含上面附加的报错内容），跟用户输入原文
            # 逐字比对年份才有意义——prompt_text 混入报错信息后，"2023"这类年份
            # 反而可能在报错文字里巧合出现，稀释掉"原文没写年份"这个判断依据。
            return parse_and_validate(raw_text, text)
        except (json.JSONDecodeError, ValidationError) as e:
            last_error = e
            print(f"[retry {attempt}/{max_attempts}] model output failed validation: {e}")

    raise ExtractionFailedError(
        # 错误消息用英文：这条会通过 HTTP 502 的 detail 传给后端/前端，
        # 是跨服务的对外文本，不是只给自己看的日志。
        f"model failed to return a schema-valid result after {max_attempts} attempts; "
        f"last error: {last_error}"
    )
