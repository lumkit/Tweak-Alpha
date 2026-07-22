#include <android/log.h>
#include <cerrno>
#include <cstdarg>
#include <csignal>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <getopt.h>
#include <string>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <unistd.h>

namespace {

constexpr char kTag[] = "tweakd";
constexpr char kVersion[] = "1.0.0";

std::string gPidPath;
std::string gSockPath;
volatile sig_atomic_t gStop = 0;

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

void onSignal(int) {
    gStop = 1;
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

bool daemonize() {
    pid_t pid = fork();
    if (pid < 0) {
        return false;
    }
    if (pid > 0) {
        _exit(0);
    }
    if (setsid() < 0) {
        return false;
    }
    signal(SIGHUP, SIG_IGN);
    pid = fork();
    if (pid < 0) {
        return false;
    }
    if (pid > 0) {
        _exit(0);
    }
    umask(0);
    chdir("/");
    int nullFd = open("/dev/null", O_RDWR);
    if (nullFd >= 0) {
        dup2(nullFd, STDIN_FILENO);
        dup2(nullFd, STDOUT_FILENO);
        dup2(nullFd, STDERR_FILENO);
        if (nullFd > STDERR_FILENO) {
            close(nullFd);
        }
    }
    return true;
}

int createListenSocket(const std::string &sockPath) {
    unlink(sockPath.c_str());
    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) {
        loge("socket failed: %s", strerror(errno));
        return -1;
    }
    sockaddr_un addr{};
    addr.sun_family = AF_UNIX;
    if (sockPath.size() >= sizeof(addr.sun_path)) {
        loge("sock path too long");
        close(fd);
        return -1;
    }
    std::snprintf(addr.sun_path, sizeof(addr.sun_path), "%s", sockPath.c_str());
    if (bind(fd, reinterpret_cast<sockaddr *>(&addr), sizeof(addr)) < 0) {
        loge("bind failed: %s", strerror(errno));
        close(fd);
        return -1;
    }
    // App UID 可直接连，无需每条命令走 shell
    chmod(sockPath.c_str(), 0666);
    if (listen(fd, 8) < 0) {
        loge("listen failed: %s", strerror(errno));
        close(fd);
        return -1;
    }
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

std::string handleCommand(const std::string &line) {
    if (line == "PING") {
        return "PONG\n";
    }
    if (line == "VERSION") {
        return std::string("OK ") + kVersion + "\n";
    }
    if (line == "STATUS") {
        char buf[256];
        snprintf(
                buf,
                sizeof(buf),
                "OK running=1 pid=%d version=%s sock=%s\n",
                getpid(),
                kVersion,
                gSockPath.c_str()
        );
        return buf;
    }
    if (line == "STOP") {
        gStop = 1;
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
            "Usage: %s --daemon --pid <path> --sock <path>\n"
            "       %s --foreground --pid <path> --sock <path>\n",
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

    if (alreadyRunning(gPidPath)) {
        loge("already running (pidfile=%s)", gPidPath.c_str());
        return 2;
    }

    if (asDaemon && !daemonize()) {
        loge("daemonize failed");
        return 1;
    }

    signal(SIGTERM, onSignal);
    signal(SIGINT, onSignal);
    signal(SIGPIPE, SIG_IGN);

    if (!writePidFile(gPidPath)) {
        return 1;
    }

    int listenFd = createListenSocket(gSockPath);
    if (listenFd < 0) {
        removeFile(gPidPath);
        return 1;
    }

    logi("tweakd %s started pid=%d sock=%s", kVersion, getpid(), gSockPath.c_str());

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

    close(listenFd);
    removeFile(gSockPath);
    removeFile(gPidPath);
    logi("tweakd stopped");
    return 0;
}
