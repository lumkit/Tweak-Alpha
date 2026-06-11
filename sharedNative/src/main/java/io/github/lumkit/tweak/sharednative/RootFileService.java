package io.github.lumkit.tweak.sharednative;

import android.content.Intent;
import android.os.IBinder;
import android.os.Process;

import com.topjohnwu.superuser.ipc.RootService;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

public final class RootFileService extends RootService {

    private final IRootFileService.Stub binder = new IRootFileService.Stub() {
        @Override
        public android.os.Bundle exists(String path) {
            try {
                return NativeFileBundles.successBoolean(NativeFileBridge.exists(path));
            } catch (Throwable throwable) {
                return NativeFileBundles.failure(throwable);
            }
        }

        @Override
        public android.os.Bundle list(String path) {
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
        public android.os.Bundle readBytes(String path) {
            try {
                return NativeFileBundles.successBytes(NativeFileBridge.readBytes(path));
            } catch (Throwable throwable) {
                return NativeFileBundles.failure(throwable);
            }
        }

        @Override
        public android.os.Bundle readText(String path) {
            try {
                byte[] bytes = NativeFileBridge.readBytes(path);
                return NativeFileBundles.successString(new String(bytes, StandardCharsets.UTF_8));
            } catch (Throwable throwable) {
                return NativeFileBundles.failure(throwable);
            }
        }

        @Override
        public android.os.Bundle writeBytes(String path, byte[] bytes) {
            try {
                NativeFileBridge.writeBytes(path, bytes);
                return NativeFileBundles.successUnit();
            } catch (Throwable throwable) {
                return NativeFileBundles.failure(throwable);
            }
        }

        @Override
        public android.os.Bundle writeText(String path, String text) {
            try {
                NativeFileBridge.writeBytes(path, text.getBytes(StandardCharsets.UTF_8));
                return NativeFileBundles.successUnit();
            } catch (Throwable throwable) {
                return NativeFileBundles.failure(throwable);
            }
        }

        @Override
        public android.os.Bundle delete(String path, boolean recursive) {
            try {
                NativeFileBridge.delete(path, recursive);
                return NativeFileBundles.successUnit();
            } catch (Throwable throwable) {
                return NativeFileBundles.failure(throwable);
            }
        }

        @Override
        public android.os.Bundle mkdirs(String path) {
            try {
                NativeFileBridge.mkdirs(path);
                return NativeFileBundles.successUnit();
            } catch (Throwable throwable) {
                return NativeFileBundles.failure(throwable);
            }
        }

        @Override
        public android.os.Bundle copy(String sourcePath, String targetPath, boolean overwrite) {
            try {
                NativeFileBridge.copy(sourcePath, targetPath, overwrite);
                return NativeFileBundles.successUnit();
            } catch (Throwable throwable) {
                return NativeFileBundles.failure(throwable);
            }
        }

        @Override
        public android.os.Bundle move(String sourcePath, String targetPath, boolean overwrite) {
            try {
                NativeFileBridge.move(sourcePath, targetPath, overwrite);
                return NativeFileBundles.successUnit();
            } catch (Throwable throwable) {
                return NativeFileBundles.failure(throwable);
            }
        }

        @Override
        public android.os.Bundle chmod(String path, String mode) {
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
