package io.github.lumkit.tweak.server;

interface ITweakServer {
    String ping();
    String status();
    void stop();
    void reloadConfig();
}
