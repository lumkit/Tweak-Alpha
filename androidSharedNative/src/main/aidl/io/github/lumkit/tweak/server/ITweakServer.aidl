package io.github.lumkit.tweak.server;

interface ITweakServer {
    String ping();
    String status();
    void stop();
    void reloadConfig();
    /** SurfaceFlinger latency FPS；在特权进程内 dumpAsync，避免 App 侧 dumpsys。 */
    float currentFps();
}
