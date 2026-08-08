"""
数据集准备（S1 "ML模型与数据集准备"任务）：把团队收集的新加坡景点原始数据
（data.gov.sg「Tourist Attractions」图层导出的 GeoJSON，见
`ML/data/raw/TouristAttractions.geojson`）清洗成推荐模型能直接用的表格。

原始数据里没有 name/description 这种通用字段名，是 GIS 导出格式的列名
（PAGETITLE/OVERVIEW/...），而且大小写不一、偶尔缺 OVERVIEW，需要先规整一遍
再喂给下游的 `content_recommender.py`。

跑法（项目根目录 Team6AdProject 下）：
    python ML/scripts/prepare_places_dataset.py
输出：ML/data/processed/singapore_attractions.csv
"""
import json
from pathlib import Path

import pandas as pd

ML_DIR = Path(__file__).resolve().parent.parent
RAW_PATH = ML_DIR / "data" / "raw" / "TouristAttractions.geojson"
OUT_PATH = ML_DIR / "data" / "processed" / "singapore_attractions.csv"


def load_raw_features() -> list[dict]:
    data = json.loads(RAW_PATH.read_text(encoding="utf-8"))
    return data["features"]


def _fix_mojibake(text: str) -> str:
    """
    原始数据源导出时把 UTF-8 文本又当 cp1252 转了一遍，撇号之类的符号变成了
    "â€™" 这种乱码（比如 "Singapore's" 变成 "Singaporeâ€™s"）。cp1252 编码
    再按 UTF-8 解码回去能还原；不是这种乱码模式的文本原样返回，不瞎改。
    """
    try:
        return text.encode("cp1252").decode("utf-8")
    except (UnicodeEncodeError, UnicodeDecodeError):
        return text


def clean_feature(feature: dict) -> dict | None:
    """
    单条 GeoJSON feature -> 一行干净数据。
    name/坐标缺失的直接丢弃（TF-IDF 至少需要 name，坐标缺失就没法算距离，
    对推荐没用）；description 缺 OVERVIEW 就退而求其次用 META_DESCRIPTION，
    两个都没有就留空字符串（TF-IDF 那边会自动只用 name 那点信息，不报错）。
    """
    props = feature.get("properties", {})
    name = (props.get("PAGETITLE") or "").strip()
    geometry = feature.get("geometry") or {}
    coords = geometry.get("coordinates")
    if not name or not coords or len(coords) < 2:
        return None

    description = (props.get("OVERVIEW") or props.get("META_DESCRIPTION") or "").strip()

    return {
        "name": _fix_mojibake(name),
        # 原始数据源本身就是"Tourist Attractions"这一个图层，没有细分类别，
        # 统一标成 attraction（跟 trip_schema.json 的 type 枚举对齐）。
        # 如果以后拿到餐厅/酒店数据集，在这里加一列区分来源图层即可。
        "type": "attraction",
        "description": _fix_mojibake(description),
        "address": _fix_mojibake((props.get("ADDRESS") or "").strip()),
        "lat": coords[1],
        "lng": coords[0],
        "opening_hours": _fix_mojibake((props.get("OPENING_HOURS") or "").strip()),
        "source_url": (props.get("EXTERNAL_LINK") or props.get("URL_PATH") or "").strip(),
    }


def main() -> None:
    rows = [clean_feature(f) for f in load_raw_features()]
    rows = [r for r in rows if r is not None]

    df = pd.DataFrame(rows).drop_duplicates(subset=["name"])
    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(OUT_PATH, index=False, encoding="utf-8-sig")

    print(f"[OK] raw {len(load_raw_features())} rows -> cleaned {len(df)} rows, written to {OUT_PATH}")
    missing_desc = (df["description"].str.strip() == "").sum()
    print(f"     {missing_desc} rows have an empty description (those match on name only, so less accurate)")


if __name__ == "__main__":
    main()
