#!/system/bin/sh
# POSIX：部分机型 /system/bin/sh 对 [[ 支持不完整

install_path="$1"
echo "busybox_install start path=$install_path"

if [ -z "$install_path" ] || [ ! -d "$install_path" ]; then
  echo "busybox_install bad path"
  exit 1
fi

cd "$install_path" || exit 1

# busybox_installed 为普通文件标记（应用侧也可识别）；兼容旧 symlink 标记
if [ -f busybox_installed ] || { [ -e busybox_1_30_1 ] && [ -e md5sum ]; }; then
  # 确保应用可检测的普通标记存在
  echo 1 > busybox_installed 2>/dev/null
  echo "busybox_install already done"
  exit 0
fi

if [ ! -f busybox ]; then
  echo "busybox_install missing binary"
  exit 1
fi

chmod 755 busybox 2>/dev/null
if ! ./busybox --help >/dev/null 2>&1; then
  echo "busybox_install binary not executable"
  ./busybox --help 2>&1 | head -n 5
  exit 1
fi

for applet in $(./busybox --list); do
  case "$applet" in
  sh|busybox|shell|swapon|swapoff|mkswap)
    ;;
  *)
    ./busybox ln -sf busybox "$applet" 2>/dev/null
    chmod 755 "$applet" 2>/dev/null
    ;;
  esac
done

./busybox ln -sf busybox busybox_1_30_1 2>/dev/null
chmod 755 busybox_1_30_1 2>/dev/null
echo 1 > busybox_installed

if [ -f busybox_installed ]; then
  echo "busybox_install ok"
  exit 0
fi

echo "busybox_install mark missing"
exit 1
