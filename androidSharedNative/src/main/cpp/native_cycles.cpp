#include "native_common.h"

#include <array>
#include <cstring>
#include <linux/perf_event.h>
#include <mutex>
#include <sstream>
#include <sys/ioctl.h>
#include <sys/syscall.h>
#include <unordered_map>

namespace {

std::mutex g_cycles_mutex;
std::unordered_map<int, int> g_cycles_fds;

long perfEventOpen(struct perf_event_attr *hw_event, pid_t pid, int cpu, int group_fd,
                   unsigned long flags) {
    return syscall(__NR_perf_event_open, hw_event, pid, cpu, group_fd, flags);
}

int openCyclesFd(int coreIndex) {
    struct perf_event_attr attr {};
    memset(&attr, 0, sizeof(attr));
    attr.type = PERF_TYPE_HARDWARE;
    attr.size = sizeof(attr);
    attr.config = PERF_COUNT_HW_CPU_CYCLES;
    attr.disabled = 0;
    attr.exclude_hv = 1;

    int fd = static_cast<int>(perfEventOpen(&attr, -1, coreIndex, -1, PERF_FLAG_FD_CLOEXEC));
    if (fd < 0) {
        return -1;
    }

    ioctl(fd, PERF_EVENT_IOC_ENABLE, 0);
    return fd;
}

long long readCyclesValue(int fd) {
    long long count = 0;
    ssize_t bytes = TEMP_FAILURE_RETRY(read(fd, &count, sizeof(count)));
    if (bytes != sizeof(count)) {
        return -1;
    }
    return count;
}

int requireCyclesFd(JNIEnv *env, int coreIndex) {
    std::lock_guard<std::mutex> lock(g_cycles_mutex);
    auto existing = g_cycles_fds.find(coreIndex);
    if (existing != g_cycles_fds.end()) {
        return existing->second;
    }

    int fd = openCyclesFd(coreIndex);
    if (fd < 0) {
        std::ostringstream stream;
        stream << "perf_event_open failed for cpu" << coreIndex << ": " << strerror(errno);
        throwIOException(env, stream.str());
    }
    g_cycles_fds[coreIndex] = fd;
    return fd;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_lumkit_tweak_sharednative_CpuCyclesBridge_readCpuCycles(JNIEnv *env, jclass,
                                                                       jint coreIndex) {
    if (coreIndex < 0) {
        throwIOException(env, "coreIndex must be >= 0");
    }

    int fd = requireCyclesFd(env, coreIndex);
    long long value = readCyclesValue(fd);
    if (value >= 0) {
        return static_cast<jlong>(value);
    }

    {
        std::lock_guard<std::mutex> lock(g_cycles_mutex);
        auto it = g_cycles_fds.find(coreIndex);
        if (it != g_cycles_fds.end()) {
            close(it->second);
            g_cycles_fds.erase(it);
        }
    }

    fd = requireCyclesFd(env, coreIndex);
    value = readCyclesValue(fd);
    if (value < 0) {
        std::ostringstream stream;
        stream << "read perf cycles failed for cpu" << coreIndex << ": " << strerror(errno);
        throwIOException(env, stream.str());
    }
    return static_cast<jlong>(value);
}
