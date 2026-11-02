#!/usr/bin/env bash
# 用工程内置的工具链编译 APK（不依赖 Android Studio）
# 用法:
#   bash tools/build_apk.sh                     # 出 debug 包
#   bash tools/build_apk.sh assembleRelease     # 出 release 包
#   bash tools/build_apk.sh test                # 只跑单元测试
set -e

HERE="$(cd "$(dirname "$0")/.." && pwd)"
TASK="${1:-assembleDebug}"

# test 是别名，避免手敲 testDebugUnitTest
if [ "$TASK" = "test" ]; then
  TASK="testDebugUnitTest"
fi

export JAVA_HOME="$(cygpath -w "$HERE/.toolchain/jdk")"
export ANDROID_HOME="$(cygpath -w "$HERE/.toolchain/android-sdk")"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

GRADLE="$HERE/.toolchain/gradle/gradle-8.9/bin/gradle.bat"
if [ ! -f "$GRADLE" ]; then
  echo "缺少 Gradle，先执行: python tools/setup_toolchain.py" >&2
  exit 1
fi

cd "$HERE"
echo "JAVA_HOME=$JAVA_HOME"
echo "ANDROID_HOME=$ANDROID_HOME"
echo "task=$TASK"

"$GRADLE" --no-daemon "$TASK"

if [ "$TASK" = "testDebugUnitTest" ]; then
  echo
  echo "测试报告: app/build/reports/tests/testDebugUnitTest/index.html"
  exit 0
fi

echo
echo "产物:"
find "$HERE/app/build/outputs/apk" -name "*.apk" -print 2>/dev/null || true
