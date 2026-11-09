#!/usr/bin/env python3
"""
搜索并发提速的排队模型实测（离散事件模拟，不做真实 sleep）。

本机没有 Kotlin/Android 工具链，无法编译 APK 实测，因此用离散事件模拟量出
「并发数 -> 总耗时」，验证 9 -> 27 的加速比。

模型与 SearchModel / ChangeBookSourceViewModel 的真实路径一一对应：
  - N 个书源，每源只发 1 个请求（一源一请求，发完即回）；
  - 并发闸 = min(threadCount, MAX) 的固定名额池（= limitedParallelism / Semaphore）；
  - 单请求耗时 = 网络往返 + 解析，取对数正态（带长尾，贴近真实书源分布）；
  - 单源失败/超时只影响该源结果，不影响其他源（= mapParallelSafe 吞异常）。

离散事件模拟：维护 (并发) 个"工位"，每次取最早空出的工位接下一个任务。
等价于固定信号量池的完成时间，无需等待真实时间，秒级出结果。
"""
import heapq
import math
import random
import statistics

N_SOURCES = 7168     # 截图里的总进度数
TIMEOUT = 15.0       # AppConst.timeLimit = 15000 ms


def make_latencies(n, seed=20261109):
    rng = random.Random(seed)
    out = []
    for _ in range(n):
        v = rng.lognormvariate(mu=math.log(0.6), sigma=0.75)
        out.append(min(v, TIMEOUT))
    return out


def simulate(latencies, concurrency):
    """固定 concurrency 个工位跑完全部任务的总耗时。"""
    # 每个工位的空闲时刻，初始都为 0
    holes = [0.0] * concurrency
    heapq.heapify(holes)
    for lat in latencies:
        free_at = heapq.heappop(holes)   # 最早空出的工位
        heapq.heappush(holes, free_at + lat)
    return max(holes)


def main():
    lat = make_latencies(N_SOURCES)
    s = sorted(lat)
    print(f"样本: {N_SOURCES} 个书源（截图实测总数）")
    print(f"单请求耗时: 中位 {statistics.median(lat):.2f}s  均值 {statistics.mean(lat):.2f}s  "
          f"P90 {s[int(len(s)*0.9)]:.2f}s  P99 {s[int(len(s)*0.99)]:.2f}s")
    print()
    print(f"{'并发':>6} {'总耗时':>10} {'相对并发9':>12} {'理想线性':>10}")
    print("-" * 44)

    base = None
    for c in (9, 16, 27, 36, 81):
        total = simulate(lat, c)
        if base is None:
            base = total
        print(f"{c:>6} {total/60:>8.1f}min {base/total:>11.2f}x {c/9:>9.1f}x")

    print()
    print("说明: 理想线性 = 纯等比；实际低于它，因为长尾任务在收尾阶段拉长总时长。")


if __name__ == "__main__":
    main()