#!/system/bin/sh
# Magisk service.d 示例：开机拉起 tweakd
# 安装：拷到 /data/adb/service.d/tweakd.sh && chmod 755
DAEMON_DIR="/data/local/tmp/TweakAlpha/daemon"
BIN="$DAEMON_DIR/tweakd"
PID="$DAEMON_DIR/tweakd.pid"
SOCK="$DAEMON_DIR/tweakd.sock"
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
setsid "$BIN" --daemon --pid "$PID" --sock "$SOCK" >>"$LOG" 2>&1 < /dev/null &
