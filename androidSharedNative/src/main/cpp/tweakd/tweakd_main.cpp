#include <android/log.h>
#include <arpa/inet.h>
#include <cerrno>
#include <cstdarg>
#include <cstdint>
#include <csignal>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <fstream>
#include <getopt.h>
#include <netinet/in.h>
#include <poll.h>
#include <string>
#include <sys/eventfd.h>
#include <sys/inotify.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <thread>
#include <time.h>
#include <unistd.h>

namespace {

constexpr char kTag[] = "tweakd";
constexpr char kVersion[] = "1.2.0";

/** conf 文件名；完整路径由 port-file 所在目录推导，与 Kotlin DaemonPaths 对齐 */
constexpr char kA11yWatchConfName[] = "a11y_watch.conf";
constexpr char kDefaultA11yComponent[] =
        "io.github.lumkit.tweak/io.github.lumkit.tweak.service.TweakAccessibilityService";
constexpr int kDefaultA11yIntervalMs = 60'000;
constexpr int kMinA11yIntervalMs = 10'000;
/** conf 目录尚不存在时，禁用态最长阻塞多久再重试 inotify watch */
constexpr int kDisabledWatchRetryMs = 60'000;

std::string gPidPath;
/** 实际为 TCP 端口文件路径（兼容旧参数名 --sock） */
std::string gSockPath;
std::string gA11yConfDir;
std::string gA11yConfPath;
int gListenPort = 0;
volatile sig_atomic_t gStop = 0;
/** 信号 / STOP 命令写入，巡检线程 poll 读出，避免短切片空转 */
int gWakeEventFd = -1;

void logi(const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    __android_log_vprint(ANDROID_LOG_INFO, kTag, fmt, args);
    va_end(args);
}

void loge(const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    __android_log_vprint(ANDROID_LOG_ERROR, kTag, fmt, args);
    va_end(args);
}

void requestWake() {
    if (gWakeEventFd < 0) {
        return;
    }
    const uint64_t one = 1;
    // eventfd 计数器饱和时 EAGAIN，可忽略
    (void) write(gWakeEventFd, &one, sizeof(one));
}

void drainWake() {
    if (gWakeEventFd < 0) {
        return;
    }
    uint64_t value = 0;
    while (read(gWakeEventFd, &value, sizeof(value)) > 0) {
    }
}

void initA11yPathsFromSock(const std::string &sockPath) {
    const auto slash = sockPath.find_last_of('/');
    if (slash == std::string::npos) {
        gA11yConfDir = ".";
    } else if (slash == 0) {
        gA11yConfDir = "/";
    } else {
        gA11yConfDir = sockPath.substr(0, slash);
    }
    gA11yConfPath = gA11yConfDir + "/" + kA11yWatchConfName;
}

void onSignal(int) {
    gStop = 1;
    requestWake();
}

bool writePidFile(const std::string &path) {
    int fd = open(path.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd < 0) {
        loge("open pidfile failed: %s", strerror(errno));
        return false;
    }
    char buf[32];
    int n = snprintf(buf, sizeof(buf), "%d\n", getpid());
    bool ok = n > 0 && write(fd, buf, static_cast<size_t>(n)) == n;
    close(fd);
    return ok;
}

void removeFile(const std::string &path) {
    if (!path.empty()) {
        unlink(path.c_str());
    }
}

bool alreadyRunning(const std::string &pidPath) {
    FILE *fp = fopen(pidPath.c_str(), "r");
    if (fp == nullptr) {
        return false;
    }
    int pid = 0;
    const int matched = fscanf(fp, "%d", &pid);
    fclose(fp);
    if (matched != 1 || pid <= 0) {
        return false;
    }
    if (kill(pid, 0) == 0) {
        return true;
    }
    // stale pidfile
    unlink(pidPath.c_str());
    return false;
}

void redirectStdioToNull() {
    int nullFd = open("/dev/null", O_RDWR);
    if (nullFd >= 0) {
        dup2(nullFd, STDIN_FILENO);
        dup2(nullFd, STDOUT_FILENO);
        dup2(nullFd, STDERR_FILENO);
        if (nullFd > STDERR_FILENO) {
            close(nullFd);
        }
    }
}

void reportToParent(int reportFd, const char *msg) {
    if (reportFd < 0 || msg == nullptr) {
        return;
    }
    const size_t len = strlen(msg);
    // 尽力写完；父进程用 read 收
    size_t off = 0;
    while (off < len) {
        const ssize_t n = write(reportFd, msg + off, len - off);
        if (n < 0) {
            if (errno == EINTR) {
                continue;
            }
            break;
        }
        off += static_cast<size_t>(n);
    }
}

/**
 * double-fork 守护化，并通过 pipe 向父进程回报启动结果。
 * 父进程（原进程）阻塞直到子进程写入 OK/ERR，再以相应退出码退出——
 * 避免以前「父进程先 _exit(0)、子进程后失败」导致 shell 看到 EXIT:0 但无 pid/sock。
 *
 * @return
 *   - 父进程：永不返回（_exit）
 *   - 孙进程：返回 reportFd（写端），调用方启动成功后写 "OK\n" 并 close
 *   - 失败：返回 -1（仅可能在首进程 fork 前）
 */
int daemonizeWithReportPipe() {
    int pipefd[2];
    if (pipe(pipefd) != 0) {
        loge("pipe failed: %s", strerror(errno));
        return -1;
    }

    const pid_t first = fork();
    if (first < 0) {
        loge("fork failed: %s", strerror(errno));
        close(pipefd[0]);
        close(pipefd[1]);
        return -1;
    }

    if (first > 0) {
        // 原进程：等待孙进程回报
        close(pipefd[1]);
        char buf[512];
        const ssize_t n = read(pipefd[0], buf, sizeof(buf) - 1);
        close(pipefd[0]);
        int status = 0;
        while (waitpid(first, &status, 0) < 0) {
            if (errno != EINTR) {
                break;
            }
        }
        if (n <= 0) {
            fprintf(stderr, "ERR daemon child reported nothing\n");
            fflush(stderr);
            _exit(1);
        }
        buf[n] = '\0';
        fputs(buf, stderr);
        fflush(stderr);
        if (strncmp(buf, "OK", 2) == 0) {
            _exit(0);
        }
        _exit(1);
    }

    // 中间进程
    close(pipefd[0]);
    if (setsid() < 0) {
        reportToParent(pipefd[1], "ERR setsid failed\n");
        close(pipefd[1]);
        _exit(1);
    }
    signal(SIGHUP, SIG_IGN);

    const pid_t second = fork();
    if (second < 0) {
        reportToParent(pipefd[1], "ERR second fork failed\n");
        close(pipefd[1]);
        _exit(1);
    }
    if (second > 0) {
        // 中间进程退出，由原进程 waitpid 回收；孙进程继续持有写端
        _exit(0);
    }

    // 孙进程：真正的 daemon
    umask(0);
    chdir("/");
    // 先不重定向 stdio，等回报完成后再丢弃，便于极端情况下的调试
    return pipefd[1];
}

/**
 * 在 127.0.0.1 上监听 TCP（端口 0 = 系统分配），把端口号写入 [portFilePath]。
 *
 * 不用 Unix Domain Socket：Shizuku(shell) 在部分机型上无法在 /data/local/tmp 创建 sock_file
 * （SELinux），但写普通文件 + 监听 loopback TCP 通常可行；App 经 127.0.0.1 直连。
 */
int createTcpListener(const std::string &portFilePath, std::string *errOut) {
    auto setErr = [&](const std::string &msg) {
        loge("%s", msg.c_str());
        if (errOut != nullptr) {
            *errOut = msg;
        }
    };

    unlink(portFilePath.c_str());

    int fd = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) {
        fd = socket(AF_INET, SOCK_STREAM, 0);
    }
    if (fd < 0) {
        setErr(std::string("socket failed: ") + strerror(errno));
        return -1;
    }
    if (fd >= 0) {
        const int flags = fcntl(fd, F_GETFD);
        if (flags >= 0) {
            fcntl(fd, F_SETFD, flags | FD_CLOEXEC);
        }
    }

    int yes = 1;
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(yes));

    sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    addr.sin_port = htons(0);

    if (bind(fd, reinterpret_cast<sockaddr *>(&addr), sizeof(addr)) < 0) {
        setErr(std::string("bind 127.0.0.1 failed: ") + strerror(errno));
        close(fd);
        return -1;
    }
    if (listen(fd, 8) < 0) {
        setErr(std::string("listen failed: ") + strerror(errno));
        close(fd);
        return -1;
    }

    sockaddr_in bound{};
    socklen_t boundLen = sizeof(bound);
    if (getsockname(fd, reinterpret_cast<sockaddr *>(&bound), &boundLen) < 0) {
        setErr(std::string("getsockname failed: ") + strerror(errno));
        close(fd);
        return -1;
    }
    gListenPort = ntohs(bound.sin_port);
    if (gListenPort <= 0) {
        setErr("invalid ephemeral port");
        close(fd);
        return -1;
    }

    const int pfd = open(portFilePath.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0666);
    if (pfd < 0) {
        setErr(std::string("open port file failed: ") + strerror(errno));
        close(fd);
        return -1;
    }
    char portBuf[32];
    const int n = snprintf(portBuf, sizeof(portBuf), "%d\n", gListenPort);
    const bool wrote = n > 0 && write(pfd, portBuf, static_cast<size_t>(n)) == n;
    close(pfd);
    if (!wrote) {
        setErr("write port file failed");
        close(fd);
        unlink(portFilePath.c_str());
        return -1;
    }
    chmod(portFilePath.c_str(), 0666);
    logi("tcp listen 127.0.0.1:%d portfile=%s", gListenPort, portFilePath.c_str());
    return fd;
}

bool writeAll(int fd, const std::string &data) {
    const char *p = data.data();
    size_t left = data.size();
    while (left > 0) {
        ssize_t n = write(fd, p, left);
        if (n < 0) {
            if (errno == EINTR) {
                continue;
            }
            return false;
        }
        p += n;
        left -= static_cast<size_t>(n);
    }
    return true;
}

std::string readLine(int fd) {
    std::string line;
    char ch = 0;
    while (true) {
        ssize_t n = read(fd, &ch, 1);
        if (n < 0) {
            if (errno == EINTR) {
                continue;
            }
            return {};
        }
        if (n == 0) {
            break;
        }
        if (ch == '\n') {
            break;
        }
        if (ch != '\r') {
            line.push_back(ch);
            if (line.size() > 4096) {
                break;
            }
        }
    }
    return line;
}

struct A11yWatchConfig {
    int intervalMs = kDefaultA11yIntervalMs;
    bool enabled = true;
    std::string component = kDefaultA11yComponent;
};

static std::string trim(std::string s) {
    while (!s.empty() && (s.front() == ' ' || s.front() == '\t' || s.front() == '\r')) {
        s.erase(s.begin());
    }
    while (!s.empty() && (s.back() == ' ' || s.back() == '\t' || s.back() == '\r' || s.back() == '\n')) {
        s.pop_back();
    }
    return s;
}

static std::string shellSingleQuote(const std::string &s) {
    std::string out = "'";
    for (char c : s) {
        if (c == '\'') {
            out += "'\\''";
        } else {
            out.push_back(c);
        }
    }
    out.push_back('\'');
    return out;
}

A11yWatchConfig readA11yConfig(const char *path) {
    A11yWatchConfig cfg;
    std::ifstream in(path);
    if (!in) {
        // 无 conf 时默认开启巡检
        return cfg;
    }
    std::string line;
    while (std::getline(in, line)) {
        line = trim(line);
        if (line.empty() || line[0] == '#') {
            continue;
        }
        const auto eq = line.find('=');
        if (eq == std::string::npos) {
            continue;
        }
        const std::string key = trim(line.substr(0, eq));
        const std::string value = trim(line.substr(eq + 1));
        if (key == "interval_ms") {
            const int ms = atoi(value.c_str());
            if (ms >= kMinA11yIntervalMs) {
                cfg.intervalMs = ms;
            }
        } else if (key == "enabled") {
            cfg.enabled = (value == "1" || value == "true" || value == "TRUE");
        } else if (key == "component") {
            if (!value.empty()) {
                cfg.component = value;
            }
        }
    }
    if (cfg.intervalMs < kMinA11yIntervalMs) {
        cfg.intervalMs = kDefaultA11yIntervalMs;
    }
    if (cfg.component.empty()) {
        cfg.component = kDefaultA11yComponent;
    }
    return cfg;
}

bool runCommandCapture(const std::string &shellCmd, int *exitCodeOut, std::string *outputOut) {
    int pipefd[2];
    if (pipe(pipefd) != 0) {
        loge("pipe for cmd failed: %s", strerror(errno));
        return false;
    }
    const pid_t pid = fork();
    if (pid < 0) {
        close(pipefd[0]);
        close(pipefd[1]);
        loge("fork cmd failed: %s", strerror(errno));
        return false;
    }
    if (pid == 0) {
        close(pipefd[0]);
        dup2(pipefd[1], STDOUT_FILENO);
        dup2(pipefd[1], STDERR_FILENO);
        if (pipefd[1] > STDERR_FILENO) {
            close(pipefd[1]);
        }
        // 恢复最小环境，避免 daemonize 后 am/cmd 缺变量直接崩
        setenv("PATH", "/system/bin:/system/xbin:/vendor/bin:/product/bin", 1);
        setenv("ANDROID_DATA", "/data", 0);
        setenv("ANDROID_ROOT", "/system", 0);
        execl("/system/bin/sh", "sh", "-c", shellCmd.c_str(), static_cast<char *>(nullptr));
        _exit(127);
    }
    close(pipefd[1]);
    std::string output;
    char buf[256];
    while (true) {
        const ssize_t n = read(pipefd[0], buf, sizeof(buf));
        if (n < 0) {
            if (errno == EINTR) {
                continue;
            }
            break;
        }
        if (n == 0) {
            break;
        }
        output.append(buf, static_cast<size_t>(n));
        if (output.size() > 4096) {
            break;
        }
    }
    close(pipefd[0]);
    int status = 0;
    while (waitpid(pid, &status, 0) < 0) {
        if (errno != EINTR) {
            loge("waitpid cmd failed: %s", strerror(errno));
            return false;
        }
    }
    int code = 255;
    if (WIFEXITED(status)) {
        code = WEXITSTATUS(status);
    } else if (WIFSIGNALED(status)) {
        code = 128 + WTERMSIG(status);
    }
    if (exitCodeOut != nullptr) {
        *exitCodeOut = code;
    }
    if (outputOut != nullptr) {
        *outputOut = output;
    }
    return true;
}

bool ensureAccessibilityEnabled(const std::string &component) {
    if (component.empty()) {
        return false;
    }

    int code = -1;
    std::string current;
    if (!runCommandCapture("settings get secure enabled_accessibility_services", &code, &current)) {
        return false;
    }
    current = trim(current);
    if (current == "null") {
        current.clear();
    }

    if (current.find(component) == std::string::npos) {
        std::string newValue;
        if (!current.empty()) {
            newValue = current + ":" + component;
        } else {
            newValue = component;
        }
        const std::string putEnabled =
                "settings put secure enabled_accessibility_services " + shellSingleQuote(newValue);
        if (!runCommandCapture(putEnabled, &code, nullptr) || code != 0) {
            loge("settings put enabled_accessibility_services failed code=%d", code);
            return false;
        }
        logi("enabled_accessibility_services=%s", newValue.c_str());
    }

    runCommandCapture("settings put secure accessibility_enabled 1", &code, nullptr);

    std::string miuiCurrent;
    if (runCommandCapture("settings get secure permitted_accessibility_services", &code, &miuiCurrent)) {
        miuiCurrent = trim(miuiCurrent);
        if (miuiCurrent == "null") {
            miuiCurrent.clear();
        }
        if (miuiCurrent.find(component) == std::string::npos) {
            std::string miuiNew;
            if (!miuiCurrent.empty()) {
                miuiNew = miuiCurrent + ":" + component;
            } else {
                miuiNew = component;
            }
            const std::string putMiui =
                    "settings put secure permitted_accessibility_services " + shellSingleQuote(miuiNew);
            if (runCommandCapture(putMiui, &code, nullptr) && code == 0) {
                logi("permitted_accessibility_services=%s", miuiNew.c_str());
            }
        }
    }
    return true;
}

bool drainInotifyConfEvents(int inotifyFd, int *confWd) {
    if (inotifyFd < 0) {
        return false;
    }
    char buf[4096]
            __attribute__((aligned(__alignof__(struct inotify_event))));
    bool confTouched = false;
    while (true) {
        const ssize_t n = read(inotifyFd, buf, sizeof(buf));
        if (n <= 0) {
            break;
        }
        for (ssize_t offset = 0; offset < n;) {
            const auto *event = reinterpret_cast<const inotify_event *>(buf + offset);
            if ((event->mask & (IN_DELETE_SELF | IN_IGNORED | IN_UNMOUNT)) != 0) {
                confTouched = true;
                if (confWd != nullptr) {
                    *confWd = -1;
                }
            } else if (event->len == 0) {
                confTouched = true;
            } else if (std::strcmp(event->name, kA11yWatchConfName) == 0) {
                confTouched = true;
            }
            offset += static_cast<ssize_t>(sizeof(inotify_event) + event->len);
        }
    }
    return confTouched;
}

bool ensureA11yConfWatch(int inotifyFd, int *wd) {
    if (inotifyFd < 0 || wd == nullptr) {
        return false;
    }
    if (*wd >= 0) {
        return true;
    }
    const int nextWd = inotify_add_watch(
            inotifyFd,
            gA11yConfDir.c_str(),
            IN_CREATE | IN_MODIFY | IN_MOVED_TO | IN_ATTRIB | IN_DELETE | IN_DELETE_SELF
    );
    if (nextWd < 0) {
        return false;
    }
    *wd = nextWd;
    logi("inotify watch ok dir=%s wd=%d", gA11yConfDir.c_str(), *wd);
    return true;
}

void clearA11yConfWatch(int inotifyFd, int *wd) {
    if (inotifyFd >= 0 && wd != nullptr && *wd >= 0) {
        inotify_rm_watch(inotifyFd, *wd);
        *wd = -1;
    }
}

/**
 * 可中断等待：poll(eventfd [, inotify])，一次睡满 timeoutMs，无 100ms 切片。
 * @param timeoutMs <0 表示无限等待（禁用采样时用）
 * @param confWd 可选；inotify watch 失效时会被置为 -1
 * @return true 表示应停止线程；false 表示超时或配置可能已变，调用方应重读 conf
 */
bool waitInterruptible(int timeoutMs, int inotifyFd, int *confWd) {
    timespec start{};
    if (timeoutMs >= 0) {
        clock_gettime(CLOCK_MONOTONIC, &start);
    }

    while (!gStop) {
        int pollTimeout = timeoutMs;
        if (timeoutMs >= 0) {
            timespec now{};
            clock_gettime(CLOCK_MONOTONIC, &now);
            const int64_t elapsedMs =
                    (now.tv_sec - start.tv_sec) * 1000LL +
                    (now.tv_nsec - start.tv_nsec) / 1000000LL;
            const int64_t left = static_cast<int64_t>(timeoutMs) - elapsedMs;
            if (left <= 0) {
                return false;
            }
            pollTimeout = left > 86'400'000LL ? 86'400'000 : static_cast<int>(left);
        }

        pollfd fds[2]{};
        int nfds = 0;
        if (gWakeEventFd >= 0) {
            fds[nfds].fd = gWakeEventFd;
            fds[nfds].events = POLLIN;
            ++nfds;
        }
        const int inotifyIndex = inotifyFd >= 0 ? nfds : -1;
        if (inotifyFd >= 0) {
            fds[nfds].fd = inotifyFd;
            fds[nfds].events = POLLIN;
            ++nfds;
        }
        if (nfds == 0) {
            // 极端兜底：无 fd 时退回一次 nanosleep
            if (pollTimeout < 0) {
                pollTimeout = kDisabledWatchRetryMs;
            }
            timespec ts{};
            ts.tv_sec = pollTimeout / 1000;
            ts.tv_nsec = (pollTimeout % 1000) * 1000000L;
            nanosleep(&ts, nullptr);
            return gStop != 0;
        }

        const int pr = poll(fds, static_cast<nfds_t>(nfds), pollTimeout);
        if (pr < 0) {
            if (errno == EINTR) {
                continue;
            }
            loge("poll failed: %s", strerror(errno));
            return gStop != 0;
        }
        if (pr == 0) {
            return gStop != 0;
        }

        bool configMaybeChanged = false;
        for (int i = 0; i < nfds; ++i) {
            if ((fds[i].revents & (POLLERR | POLLHUP | POLLNVAL)) != 0) {
                if (i == inotifyIndex) {
                    configMaybeChanged = true;
                    if (confWd != nullptr) {
                        *confWd = -1;
                    }
                }
                continue;
            }
            if ((fds[i].revents & POLLIN) == 0) {
                continue;
            }
            if (fds[i].fd == gWakeEventFd) {
                drainWake();
            } else if (i == inotifyIndex) {
                if (drainInotifyConfEvents(inotifyFd, confWd)) {
                    configMaybeChanged = true;
                }
            }
        }

        if (gStop) {
            return true;
        }
        if (configMaybeChanged) {
            return false;
        }
        // 仅有虚假 wake 时继续等剩余时间
    }
    return true;
}

void a11yWatchThreadMain() {
    logi("a11y watch thread started conf=%s", gA11yConfPath.c_str());
    int inotifyFd = inotify_init1(IN_CLOEXEC | IN_NONBLOCK);
    if (inotifyFd < 0) {
        loge("inotify_init1 failed: %s", strerror(errno));
    }
    int confWd = -1;

    while (!gStop) {
        if (inotifyFd >= 0 && !ensureA11yConfWatch(inotifyFd, &confWd)) {
            // 目录尚不存在：禁用态会按 kDisabledWatchRetryMs 重试
        }

        const A11yWatchConfig cfg = readA11yConfig(gA11yConfPath.c_str());
        if (cfg.enabled && !cfg.component.empty()) {
            if (!ensureAccessibilityEnabled(cfg.component)) {
                // 失败也继续按间隔重试
            }
            const int interval =
                    cfg.intervalMs >= kMinA11yIntervalMs ? cfg.intervalMs : kDefaultA11yIntervalMs;
            if (waitInterruptible(interval, inotifyFd, &confWd)) {
                break;
            }
            continue;
        }

        // 巡检关闭：阻塞到 conf 变更 / STOP，不空转
        if (confWd < 0) {
            if (waitInterruptible(kDisabledWatchRetryMs, /*inotifyFd=*/-1, /*confWd=*/nullptr)) {
                break;
            }
            continue;
        }
        if (waitInterruptible(/*timeoutMs=*/-1, inotifyFd, &confWd)) {
            break;
        }
    }

    clearA11yConfWatch(inotifyFd, &confWd);
    if (inotifyFd >= 0) {
        close(inotifyFd);
    }
    logi("a11y watch thread stopped");
}

std::string handleCommand(const std::string &line) {
    if (line == "PING") {
        return "PONG\n";
    }
    if (line == "VERSION") {
        return std::string("OK ") + kVersion + "\n";
    }
    if (line == "STATUS") {
        const A11yWatchConfig cfg = readA11yConfig(gA11yConfPath.c_str());
        char buf[640];
        snprintf(
                buf,
                sizeof(buf),
                "OK running=1 pid=%d version=%s tcp=127.0.0.1:%d portfile=%s a11y_watch=1 a11y_enabled=%d a11y_interval_ms=%d idle_poll=1\n",
                getpid(),
                kVersion,
                gListenPort,
                gSockPath.c_str(),
                cfg.enabled ? 1 : 0,
                cfg.intervalMs
        );
        return buf;
    }
    if (line == "STOP") {
        gStop = 1;
        requestWake();
        return "OK stopping\n";
    }
    return "ERR unknown_command\n";
}

void serveClient(int clientFd) {
    while (!gStop) {
        std::string line = readLine(clientFd);
        if (line.empty()) {
            break;
        }
        std::string resp = handleCommand(line);
        if (!writeAll(clientFd, resp)) {
            break;
        }
        if (line == "STOP") {
            break;
        }
    }
    close(clientFd);
}

void printUsage(const char *argv0) {
    fprintf(
            stderr,
            "Usage: %s --daemon --pid <path> --sock <portfile>\n"
            "       %s --foreground --pid <path> --sock <portfile>\n"
            "  --sock  path to write TCP listen port (127.0.0.1)\n",
            argv0,
            argv0
    );
}

} // namespace

int main(int argc, char **argv) {
    bool asDaemon = false;
    bool foreground = false;

    static option longOpts[] = {
            {"daemon",     no_argument,       nullptr, 'd'},
            {"foreground", no_argument,       nullptr, 'f'},
            {"pid",        required_argument, nullptr, 'p'},
            {"sock",       required_argument, nullptr, 's'},
            {"help",       no_argument,       nullptr, 'h'},
            {nullptr, 0,                      nullptr, 0},
    };

    int opt;
    while ((opt = getopt_long(argc, argv, "dfp:s:h", longOpts, nullptr)) != -1) {
        switch (opt) {
            case 'd':
                asDaemon = true;
                break;
            case 'f':
                foreground = true;
                break;
            case 'p':
                gPidPath = optarg;
                break;
            case 's':
                gSockPath = optarg;
                break;
            case 'h':
            default:
                printUsage(argv[0]);
                return opt == 'h' ? 0 : 1;
        }
    }

    if ((!asDaemon && !foreground) || gPidPath.empty() || gSockPath.empty()) {
        printUsage(argv[0]);
        return 1;
    }

    initA11yPathsFromSock(gSockPath);

    if (alreadyRunning(gPidPath)) {
        loge("already running (pidfile=%s)", gPidPath.c_str());
        fprintf(stderr, "ERR already running pidfile=%s\n", gPidPath.c_str());
        return 2;
    }

    int reportFd = -1;
    if (asDaemon) {
        reportFd = daemonizeWithReportPipe();
        if (reportFd < 0) {
            fprintf(stderr, "ERR daemonizeWithReportPipe failed\n");
            return 1;
        }
    }

    auto failStartup = [&](const char *msg) {
        loge("%s", msg);
        if (reportFd >= 0) {
            reportToParent(reportFd, msg);
            close(reportFd);
            reportFd = -1;
        } else {
            fprintf(stderr, "%s", msg);
            fflush(stderr);
        }
        if (gWakeEventFd >= 0) {
            close(gWakeEventFd);
            gWakeEventFd = -1;
        }
        removeFile(gPidPath);
        removeFile(gSockPath);
        // daemon 子进程用 _exit，避免冲刷其它状态
        if (asDaemon) {
            _exit(1);
        }
        return 1;
    };

    gWakeEventFd = eventfd(0, EFD_CLOEXEC | EFD_NONBLOCK);
    if (gWakeEventFd < 0) {
        char buf[128];
        snprintf(buf, sizeof(buf), "ERR eventfd failed: %s\n", strerror(errno));
        return failStartup(buf);
    }

    signal(SIGTERM, onSignal);
    signal(SIGINT, onSignal);
    signal(SIGPIPE, SIG_IGN);

    if (!writePidFile(gPidPath)) {
        return failStartup("ERR writePidFile failed\n");
    }

    std::string listenErr;
    int listenFd = createTcpListener(gSockPath, &listenErr);
    if (listenFd < 0) {
        const std::string msg = "ERR createListenSocket failed: " + listenErr + "\n";
        return failStartup(msg.c_str());
    }

    if (reportFd >= 0) {
        reportToParent(reportFd, "OK\n");
        close(reportFd);
        reportFd = -1;
        redirectStdioToNull();
    }

    logi(
            "tweakd %s started pid=%d tcp=127.0.0.1:%d portfile=%s",
            kVersion,
            getpid(),
            gListenPort,
            gSockPath.c_str()
    );

    std::thread a11yThread(a11yWatchThreadMain);

    while (!gStop) {
        int client = accept4(listenFd, nullptr, nullptr, SOCK_CLOEXEC);
        if (client < 0) {
            if (errno == EINTR) {
                continue;
            }
            if (gStop) {
                break;
            }
            loge("accept failed: %s", strerror(errno));
            break;
        }
        serveClient(client);
    }

    gStop = 1;
    requestWake();
    if (a11yThread.joinable()) {
        a11yThread.join();
    }

    close(listenFd);
    close(gWakeEventFd);
    gWakeEventFd = -1;
    removeFile(gSockPath);
    removeFile(gPidPath);
    logi("tweakd stopped");
    return 0;
}
