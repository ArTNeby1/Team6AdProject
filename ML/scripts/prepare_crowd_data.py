"""
F-33"人流预测"的数据清洗：把 data.gov.sg 的全国季度旅游收入统计，
清洗成一份"季节性指数"——哪个季度历史上更旺、哪个更淡。

**先把范围说死，这是这个原型最重要的一点，答辩必问**：

`TourismReceiptsByMajorComponentsYearToDateQuarterly.csv` 是**全国季度旅游总收入**
（新元百万，2008 Q1 ~ 2025 Q4，6 个大类），**不是**景点级别的人流数据。
backlog 里 F-33 原话是"预测景点人流量"，这份数据做不到——它给不了"滨海湾花园
今天下午几点人多"，只能给"整体来说，第几季度新加坡游客通常更多"。

所以这里做的是**季节性代理信号**，不是真人流预测：拿"Sightseeing, Entertainment
& Gaming"这一类（6 类里跟"逛景点"关联最强的一类，比总收入或住宿/餐饮更贴近）
按季度算一个相对指数，用来回答"这个季度是旺季还是淡季"，回答不了"这一天""这个
景点"级别的问题。

**排除了 2020~2022 三年**：这三年是疫情封关期，数值暴跌不是因为季节性
（不是"这个季度人少"，是"这年根本没有游客能来"），混进去算平均会把疫情当成
季节规律，是明显的数据污染，必须排除，不是随便丢弃数据。

**关键一步（做错会得出完全错误的结论，务必先做）：先"去累计"**。
文件名里的 "YearToDate" 不是随便叫的——每一列不是"这个季度自己的收入"，
是"从今年 1 月累计到这个季度末"的总数。实测验证：2008 年 Sightseeing 这一类
Q1~Q4 依次是 37 / 72 / 113 / 177，逐季只涨不跌，是典型的累计序列特征。
如果直接拿这些数字当"单季度独立数值"去算占比，Q4 永远看起来比 Q1 大好几倍——
不是因为第四季度真的更旺，是因为 Q4 那一列本来就包含了 Q1+Q2+Q3+Q4 全年。
所以第一步必须做减法还原出每季度自己的量：Q1 独立值 = YTD(Q1)；
Q2 独立值 = YTD(Q2) - YTD(Q1)；Q3、Q4 同理。

**用"占全年比例"而不是直接平均去累计后的数值**：不同年份的旅游业整体规模差很多
（2008 年和 2024 年不是一个量级），直接比较数值，指数会被"今年比去年更火"
这种趋势效应主导，掩盖真正的季节规律。改成"这一季度独立值占当年全年收入的比例"，
每年这四个比例自己就先归一化过一次（相加=1），再跨年取平均，这样年份之间的
整体规模差异被消掉了，剩下的才是纯粹的季节形状。

跑法（在 ML/ 目录下）：
    python scripts/prepare_crowd_data.py
"""
import re
import sys
from pathlib import Path

import pandas as pd

sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")  # Windows 终端默认 GBK

RAW_PATH = Path(__file__).resolve().parent.parent / "data" / "raw" / "TourismReceiptsByMajorComponentsYearToDateQuarterly.csv"
OUT_PATH = Path(__file__).resolve().parent.parent / "data" / "processed" / "crowd_seasonal_index.csv"

TARGET_ROW = "Sightseeing, Entertainment & Gaming"

# 疫情封关导致的数值异常年份，不代表正常季节规律，排除。
# 2023 年边境已基本重开、数值回升，保留在样本内。
EXCLUDED_YEARS = {2020, 2021, 2022}

COLUMN_PATTERN = re.compile(r"^(\d{4})(\d)Q$")  # 例如 "20254Q" -> year=2025, quarter=4


def _parse_columns(columns) -> list[tuple[str, int, int]]:
    """把 "20254Q" 这种列名拆成 (原始列名, year, quarter)。"""
    parsed = []
    for col in columns:
        m = COLUMN_PATTERN.match(str(col).strip())
        if m:
            parsed.append((col, int(m.group(1)), int(m.group(2))))
    return parsed


def build_seasonal_index() -> pd.DataFrame:
    df = pd.read_csv(RAW_PATH)
    row = df[df["DataSeries"].str.strip() == TARGET_ROW]
    if row.empty:
        raise ValueError(f"找不到行 {TARGET_ROW!r}，原始 CSV 的 DataSeries 列可能变了")
    values = row.iloc[0]

    columns = _parse_columns(df.columns)
    by_year: dict[int, dict[int, float]] = {}
    for col, year, quarter in columns:
        if year in EXCLUDED_YEARS:
            continue
        by_year.setdefault(year, {})[quarter] = float(values[col])

    # 只保留四个季度都有数据的年份——去累计和算占比都需要完整的 4 个点
    complete_years = {y: q for y, q in by_year.items() if len(q) == 4}

    ratios_by_quarter: dict[int, list[float]] = {1: [], 2: [], 3: [], 4: []}
    for year, ytd in complete_years.items():
        # 去累计：每季度自己的量 = 这季度的累计值 - 上一季度的累计值（Q1 前面没有上季度，就是它自己）
        standalone, prev = {}, 0.0
        for q in (1, 2, 3, 4):
            standalone[q] = ytd[q] - prev
            prev = ytd[q]

        if any(v < 0 for v in standalone.values()):
            # 累计值理论上只增不减，减出负数说明这年数据有问题，整年跳过，不硬算
            print(f"[skip] {year} 年去累计后出现负值 {standalone}，原始数据可能有误，跳过这一年")
            continue

        year_total = ytd[4]  # Q4 的累计值本身就是全年总数，不用再单独 sum 一次
        if year_total <= 0:
            continue  # 防御一下除零
        for q, v in standalone.items():
            ratios_by_quarter[q].append(v / year_total)

    rows = []
    for q in (1, 2, 3, 4):
        ratios = ratios_by_quarter[q]
        avg_ratio = sum(ratios) / len(ratios)
        rows.append(
            {
                "quarter": q,
                "seasonal_index": round(avg_ratio, 4),
                "n_years_used": len(ratios),
            }
        )

    result = pd.DataFrame(rows)
    # 理论上四季度的平均占比加起来应该 ≈ 1.0（每年内部本来就是占比之和=1，
    # 跨年取平均不会破坏这个性质）——留一个检查，跑出来不对说明代码有问题。
    total = result["seasonal_index"].sum()
    assert abs(total - 1.0) < 0.01, f"四季度指数之和应该接近 1.0，实际 {total}——检查计算逻辑"

    return result


if __name__ == "__main__":
    result = build_seasonal_index()
    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    result.to_csv(OUT_PATH, index=False)

    print(f"用了 {result['n_years_used'].iloc[0]} 个完整年份的数据（已排除 {sorted(EXCLUDED_YEARS)}）\n")
    print(result.to_string(index=False))
    print(f"\n四季度指数之和：{result['seasonal_index'].sum():.4f}（应接近 1.0）")
    print(f"\n已写入 {OUT_PATH}")

    busiest = result.loc[result["seasonal_index"].idxmax()]
    quietest = result.loc[result["seasonal_index"].idxmin()]
    print(f"\n历史上最旺：Q{int(busiest['quarter'])}（指数 {busiest['seasonal_index']}）")
    print(f"历史上最淡：Q{int(quietest['quarter'])}（指数 {quietest['seasonal_index']}）")
