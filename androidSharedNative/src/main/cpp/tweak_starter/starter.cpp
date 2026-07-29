#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <limits.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/types.h>

#define EXIT_FATAL_SET_CLASSPATH 3
#define EXIT_FATAL_FORK 4
#define EXIT_FATAL_APP_PROCESS 5
#define EXIT_FATAL_UID 6
#define EXIT_FATAL_PM_PATH 7

#define PACKAGE_NAME "io.github.lumkit.tweak"
#define SERVER_NAME "tweak_server"
#define SERVER_CLASS_PATH "io.github.lumkit.tweak.server.TweakServerMain"
#define BOOT_LOG_PATH "/data/local/tmp/tweak-alpha/daemon/starter.boot.log"

static void boot_log(const char *msg) {
    const int fd = open(BOOT_LOG_PATH, O_WRONLY | O_CREAT | O_APPEND, 0666);
    if (fd < 0) return;
    dprintf(fd, "pid=%d uid=%d %s\n", getpid(), getuid(), msg);
    close(fd);
}

static char *trim(char *str) {
    if (str == nullptr) return nullptr;
    while (*str != '\0' && (*str == ' ' || *str == '\n' || *str == '\r' || *str == '\t')) {
        ++str;
    }
    size_t len = strlen(str);
    while (len > 0) {
        char c = str[len - 1];
        if (c != ' ' && c != '\n' && c != '\r' && c != '\t') break;
        str[--len] = '\0';
    }
    return str;
}

static void sanitize_android_env() {
    // file_service 等父进程可能泄漏 CLASSPATH/Zygote 变量，导致嵌套 app_process 秒退
    unsetenv("CLASSPATH");
    unsetenv("LD_LIBRARY_PATH");
    unsetenv("ANDROID_SOCKET_zygote");
    unsetenv("ANDROID_SOCKET_usap1");
    unsetenv("ANDROID_SOCKET_usap2");
    unsetenv("ANDROID_ENTRYPOINT");
    setenv("ANDROID_DATA", "/data", 1);
    setenv("ANDROID_ROOT", "/system", 1);
    setenv("PATH", "/system/bin:/system/xbin:/vendor/bin:/product/bin", 1);
}

static void run_server(const char *dex_path, const char *package_name) {
    sanitize_android_env();
    boot_log("run_server after sanitize_android_env");

    if (setenv("CLASSPATH", dex_path, 1) != 0) {
        boot_log("fatal: can't set CLASSPATH");
        fprintf(stderr, "fatal: can't set CLASSPATH\n");
        exit(EXIT_FATAL_SET_CLASSPATH);
    }

    char class_path_arg[PATH_MAX];
    snprintf(class_path_arg, sizeof(class_path_arg), "-Djava.class.path=%s", dex_path);

    char nice_name_arg[64];
    snprintf(nice_name_arg, sizeof(nice_name_arg), "--nice-name=%s", SERVER_NAME);

    char package_arg[256];
    snprintf(package_arg, sizeof(package_arg), "--package=%s", package_name);

    char msg[PATH_MAX + 64];
    snprintf(msg, sizeof(msg), "exec app_process dex=%s", dex_path);
    boot_log(msg);

    char *const argv[] = {
            const_cast<char *>("/system/bin/app_process"),
            class_path_arg,
            const_cast<char *>("/system/bin"),
            nice_name_arg,
            const_cast<char *>(SERVER_CLASS_PATH),
            package_arg,
            nullptr
    };

    execvp(argv[0], argv);
    snprintf(msg, sizeof(msg), "fatal: exec app_process failed errno=%d", errno);
    boot_log(msg);
    fprintf(stderr, "fatal: exec app_process failed, errno=%d\n", errno);
    exit(EXIT_FATAL_APP_PROCESS);
}

static void start_server(const char *dex_path, const char *package_name) {
    // 双重 fork：彻底脱离 Shizuku newProcess / shell 进程组，避免命令结束时被带走
    int pipefd[2];
    if (pipe(pipefd) != 0) {
        boot_log("fatal: pipe failed");
        fprintf(stderr, "fatal: can't pipe\n");
        exit(EXIT_FATAL_FORK);
    }

    const pid_t first = fork();
    if (first < 0) {
        boot_log("fatal: first fork failed");
        fprintf(stderr, "fatal: can't fork\n");
        exit(EXIT_FATAL_FORK);
    }
    if (first > 0) {
        close(pipefd[1]);
        char buf[32] = {0};
        const ssize_t n = read(pipefd[0], buf, sizeof(buf) - 1);
        close(pipefd[0]);
        int reported = first;
        if (n > 0) {
            reported = atoi(buf);
        }
        printf("info: tweak_server pid is %d\n", reported);
        printf("info: tweak_starter exit with 0\n");
        exit(EXIT_SUCCESS);
    }

    close(pipefd[0]);
    if (setsid() < 0) {
        boot_log("setsid failed");
    }

    const pid_t second = fork();
    if (second < 0) {
        close(pipefd[1]);
        boot_log("fatal: second fork failed");
        _exit(EXIT_FATAL_FORK);
    }
    if (second > 0) {
        close(pipefd[1]);
        _exit(EXIT_SUCCESS);
    }

    {
        char buf[32];
        const int len = snprintf(buf, sizeof(buf), "%d", getpid());
        if (len > 0) {
            write(pipefd[1], buf, static_cast<size_t>(len));
        }
        close(pipefd[1]);
    }

    boot_log("grandchild alive, preparing stdio");
    chdir("/");

    // stderr/stdout 落到 boot log，便于抓 app_process abort
    const int logfd = open(BOOT_LOG_PATH, O_WRONLY | O_CREAT | O_APPEND, 0666);
    const int nullfd = open("/dev/null", O_RDWR);
    if (nullfd != -1) {
        dup2(nullfd, STDIN_FILENO);
        if (nullfd > 2) close(nullfd);
    }
    if (logfd != -1) {
        dup2(logfd, STDOUT_FILENO);
        dup2(logfd, STDERR_FILENO);
        if (logfd > 2) close(logfd);
    }

    run_server(dex_path, package_name);
}

int main(int argc, char *argv[]) {
    const uid_t uid = getuid();
    if (uid != 0 && uid != 2000) {
        fprintf(stderr, "fatal: run from non root nor adb user (uid=%d)\n", uid);
        exit(EXIT_FATAL_UID);
    }

    boot_log("starter main enter");

    const char *apk_path = nullptr;
    const char *package_name = PACKAGE_NAME;
    for (int i = 1; i < argc; ++i) {
        if (strncmp(argv[i], "--apk=", 6) == 0) {
            apk_path = argv[i] + 6;
        } else if (strncmp(argv[i], "--package=", 10) == 0) {
            package_name = argv[i] + 10;
        }
    }

    char resolved_apk_path[PATH_MAX] = {0};
    if (apk_path == nullptr || access(apk_path, R_OK) != 0) {
        char cmd[256];
        snprintf(cmd, sizeof(cmd), "pm path %s", package_name);
        FILE *f = popen(cmd, "r");
        if (f != nullptr) {
            char line[PATH_MAX] = {0};
            if (fgets(line, sizeof(line), f) != nullptr) {
                char *trimmed = trim(line);
                if (strncmp(trimmed, "package:", 8) == 0) {
                    snprintf(resolved_apk_path, sizeof(resolved_apk_path), "%s", trimmed + 8);
                    apk_path = resolved_apk_path;
                }
            }
            pclose(f);
        }
    }

    if (apk_path == nullptr || access(apk_path, R_OK) != 0) {
        boot_log("fatal: can't get readable apk path");
        fprintf(stderr, "fatal: can't get readable apk path\n");
        exit(EXIT_FATAL_PM_PATH);
    }

    start_server(apk_path, package_name);
}
