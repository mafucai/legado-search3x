#!/bin/bash
# 用真实 Kotlin 2.1.0 编译器，对改动文件做「改前 vs 改后」差分验证。
#
# 判定原理（避开缺依赖噪声）：
#   同一文件分别用【原始备份】和【改动后】编译，把报错归一化后做集合差。
#   - 改后没有新增任何错误 -> 未引入新问题（PASS）
#   - 出现 parser 级语法错误 -> 直接 FAIL
#   - 编译器自身异常 / 输出异常短 -> 判"验证无效"，绝不当 PASS
#
# 依赖：/opt/jdk21 + /opt/kotlin/lib/*.jar（含 annotations-13.0.jar）
#
# ★ 关键坑（实测踩过）：把 "$JAVA -cp $LIB/* ..." 存进变量再 "$KOTLINC" 执行时，
#   `*` 不会展开（变量在函数里被当作单个词），编译器 classpath 只剩部分 jar，
#   静默失败只输出 191 字节 warning，看起来像"0 错误"的假通过。
#   所以这里用 bash 数组传参，让 * 正常展开。

set -u
JAVA=/opt/jdk21/bin/java
LIB=/opt/kotlin/lib
CP="$LIB/kotlin-stdlib-2.1.0.jar:$LIB/kotlinx-coroutines-core-jvm-1.10.1.jar"

# ★ 必须拼成单个冒号分隔的 classpath 字符串。
#   写成 -cp "${JARS[@]}" 会把每个 jar 当成独立参数，java 只认第一个，
#   编译器因此缺 jar 静默失败（实测输出仅 191 字节 = 假通过）。
JARLIST=$(printf '%s:' "$LIB"/*.jar); JARLIST=${JARLIST%:}

raw_of() {
  "$JAVA" -cp "$JARLIST" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -no-stdlib -nowarn -d /tmp/kcout -cp "$CP" "$1" 2>&1
}

# 归一化：只留 error 行，抹掉 "路径:行:列: " 前缀。
# 必须抹掉路径，因为"改前"是从备份复制到 /tmp/before_copy.kt 编译的，
# 路径与"改后"不同；不抹就会把同一错误算成两条，差分全假。
norm_of() { grep -E "error:" | sed -E 's#^[^ ]*\.kt:[0-9]+:[0-9]+: ##' | sort -u; }

fail=0
echo "================ 编译器自检（防假通过） ================"
# 自检 1: 编译器必须能识别 AppConst.kt 里的 KMP expect 错误（证明语义分析真跑起来了）
selfcheck=$(raw_of "foundation/src/commonMain/kotlin/io/legado/app/constant/AppConst.kt")
if ! echo "$selfcheck" | grep -q "multiplatform projects"; then
  echo "INVALID 编译器自检失败（未报出已知的 KMP expect 错误），classpath 或环境有问题："
  echo "$selfcheck" | head -5 | sed 's/^/    /'
  exit 2
fi
echo "OK  编译器正常（成功识别已知的 KMP expect 错误 = 语义分析真在跑）"

# 自检 2: 故意插语法错必须被抓到（证明 parser 真在跑）
cp data/src/commonMain/kotlin/io/legado/app/model/webBook/SearchModel.kt /tmp/_probe.kt
sed -i 's/get() = min(threadCount, AppConst.MAX_SEARCH_THREAD)/get() = min(threadCount AppConst.MAX_SEARCH_THREAD/' /tmp/_probe.kt
probe=$(raw_of /tmp/_probe.kt)
if ! echo "$probe" | grep -qi "expecting an element\|syntax error"; then
  echo "INVALID 编译器自检失败（故意插入的语法错未被识别）"
  rm -f /tmp/_probe.kt; exit 2
fi
echo "OK  编译器能抓到故意插入的语法错（parser 正常工作）"
rm -f /tmp/_probe.kt

echo
echo "================ 语法错误直查（parser 级） ================"
for f in "$@"; do
  raw=$(raw_of "$f")
  if echo "$raw" | grep -q "Backend Internal error\|NoClassDefFoundError"; then
    echo "INVALID $(basename "$f")  ★编译器崩溃"; fail=1; continue
  fi
  syn=$(echo "$raw" | grep -Ei 'expecting an element|unexpected token|syntax error' | head -5)
  if [ -n "$syn" ]; then
    echo "FAIL $(basename "$f")"; echo "$syn" | sed 's/^/     /'; fail=1
  else
    n=$(echo "$raw" | grep -c "error:")
    echo "OK   $(basename "$f")   无语法错误（缺依赖噪声 ${n} 条，属预期）"
  fi
done

echo
echo "================ 改前/改后 差分 ================"
for f in "$@"; do
  bak="$f.bak-20261109"
  [ -f "$bak" ] || { echo "SKIP $(basename "$f")  (无备份)"; continue; }

  # ★ 编译器拒绝非 .kt 后缀（报 "source entry is not a Kotlin file"），
  #   所以必须把备份复制成 .kt 再编译，否则"改前"等于没编译，差分全假。
  cp "$bak" /tmp/before_copy.kt
  raw_of /tmp/before_copy.kt > /tmp/before.raw 2>&1
  raw_of "$f"                > /tmp/after.raw  2>&1
  if cat /tmp/before.raw /tmp/after.raw | grep -q "Backend Internal error"; then
    echo "INVALID $(basename "$f")  ★编译器崩溃，差分无效"; fail=1; continue
  fi

  norm_of < /tmp/before.raw > /tmp/before.norm
  norm_of < /tmp/after.raw  > /tmp/after.norm

  nb=$(wc -l < /tmp/before.norm); na=$(wc -l < /tmp/after.norm)
  new=$(comm -13 /tmp/before.norm /tmp/after.norm | grep -v '^$')

  if [ -z "$new" ]; then
    echo "PASS $(basename "$f")   改后未新增错误（改前 ${nb} 类 / 改后 ${na} 类）"
  else
    echo "FAIL $(basename "$f")   改后新增 $(echo "$new" | wc -l) 类:"
    echo "$new" | head -8 | sed 's/^/        /'
    fail=1
  fi
done

echo
[ "$fail" = "0" ] && echo "==> 总判定: PASS" || echo "==> 总判定: FAIL / INVALID"
exit $fail