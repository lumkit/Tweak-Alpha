package io.github.lumkit.tweak.server;

interface ITweakServer {
    String ping();
    String status();
    void stop();
    void reloadConfig();
    /** SurfaceFlinger latency FPS；在特权进程内 dumpAsync。@param latencySource 1=第二列 2=第三列 */
    float currentFps(int latencySource);
}
