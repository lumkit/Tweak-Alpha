package io.github.lumkit.tweak.sharednative;

import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import com.topjohnwu.superuser.ipc.RootService;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

public final class RootFileService extends RootService {

    private final IRootFileService.Stub binder = new IRootFileService.Stub() {
        @Override
        public Bundle exists(String path) {
            try {
                return NativeFileBundles.successBoolean(NativeFileBridge.exists(path));
            } catch (Throwable throwable) {
                return NativeFileBundles.failure(throwable);
            }
        }

        @Override
        public Bundle list(String path) {
            try {
                String[] entries = NativeFileBridge.list(path);
                return NativeFileBundles.successStringList(
                    entries != null ? Arrays.asList(entries) : Collections.emptyList()
                );
            } catch (Throwable throwable) {
                return NativeFileBundles.failure(throwable);
            }
        }

        @Override
        public Bundle readBytes(String path) {
            try {
                return NativeFileBundles.successBytes(NativeFileBridge.readBytes(path));
            } catch (Throwable throwable) {
                return NativeFileBundles.failure(throwable);
            }
        }

        @Override
        public Bundle readText(String path) {
            try {
                byte[] bytes = NativeFileBridge.readBytes(path);
                return NativeFileBundles.successString(new String(bytes, StandardCharsets.UTF_8));
            } catch (Throwable throwable) {
                return NativeFileBundles.failure(throwable);
            }
        }

        @Override
        public Bundle writeBytes(String path, byte[] bytes) {
            try {
                NativeFileBridge.writeBytes(path, bytes);
                return NativeFileBundles.successUnit();
            } catch (Throwable throwable) {
                return NativeFileBundles.failure(throwable);
            }
        }

        @Override
        public Bundle writeText(String path, String text) {
            try {
                NativeFileBridge.writeBytes(path, text.getBytes(StandardCharsets.UTF_8));
                return NativeFileBundles.successUnit();
            } catch (Throwable throwable) {
                return NativeFileBundles.failure(throwable);
            }
        }

        @Override
        public Bundle delete(String path, boolean recursive) {
            try {
                NativeFileBridge.delete(path, recursive);
                return NativeFileBundles.successUnit();
            } catch (Throwable throwable) {
                return NativeFileBundles.failure(throwable);
            }
        }

        @Override
        public Bundle mkdirs(String path) {
            try {
                NativeFileBridge.mkdirs(path);
                return NativeFileBundles.successUnit();
            } catch (Throwable throwable) {
                return NativeFileBundles.failure(throwable);
            }
        }

        @Override
        public Bundle copy(String sourcePath, String targetPath, boolean overwrite) {
            try {
                NativeFileBridge.copy(sourcePath, targetPath, overwrite);
                return NativeFileBundles.successUnit();
            } catch (Throwable throwable) {
                return NativeFileBundles.failure(throwable);
            }
        }

        @Override
        public Bundle move(String sourcePath, String targetPath, boolean overwrite) {
            try {
                NativeFileBridge.move(sourcePath, targetPath, overwrite);
                return NativeFileBundles.successUnit();
            } catch (Throwable throwable) {
                return NativeFileBundles.failure(throwable);
            }
        }

        @Override
        public Bundle chmod(String path, String mode) {
            try {
                NativeFileBridge.chmod(path, mode);
                return NativeFileBundles.successUnit();
            } catch (Throwable throwable) {
                return NativeFileBundles.failure(throwable);
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
