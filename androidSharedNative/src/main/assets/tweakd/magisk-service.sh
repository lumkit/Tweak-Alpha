#!/system/bin/sh
# Magisk service.d 示例：开机拉起 tweakd（Root 私有目录）
# 安装：拷到 /data/adb/service.d/tweakd.sh && chmod 755
DAEMON_DIR="/data/adb/tweak-alpha/daemon"
BIN="$DAEMON_DIR/tweakd"
PID="$DAEMON_DIR/tweakd.pid"
# 端口文件（TCP 127.0.0.1），兼容 tweakd --sock 参数名
SOCK="$DAEMON_DIR/tweakd.port"
LOG="$DAEMON_DIR/tweakd.log"

if [ ! -x "$BIN" ]; then
  exit 0
fi

if [ -f "$PID" ]; then
  old="$(cat "$PID" 2>/dev/null)"
  if [ -n "$old" ] && [ -d "/proc/$old" ]; then
    exit 0
  fi
fi

mkdir -p "$DAEMON_DIR"
chmod 700 /data/adb/tweak-alpha "$DAEMON_DIR" 2>/dev/null || true
setsid "$BIN" --daemon --pid "$PID" --sock "$SOCK" >>"$LOG" 2>&1 < /dev/null &
