package io.github.lumkit.tweak.sharednative;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;

interface IRootFileService {
    Bundle exists(String path);
    Bundle list(String path);
    Bundle listEntries(String path);
    Bundle zipEntries(String path);
    ParcelFileDescriptor openReadOnlyFd(String path);
    ParcelFileDescriptor openWriteOnlyFd(String path, boolean create, boolean truncate);
    Bundle readBytes(String path);
    Bundle readText(String path);
    Bundle writeBytes(String path, in byte[] bytes);
    Bundle writeText(String path, String text);
    Bundle delete(String path, boolean recursive);
    Bundle mkdirs(String path);
    Bundle copy(String sourcePath, String targetPath, boolean overwrite);
    Bundle move(String sourcePath, String targetPath, boolean overwrite);
    Bundle chmod(String path, String mode);
    Bundle readCpuCycles(int coreIndex);
    Bundle listInstalledApps();
    Bundle getInstalledApp(String packageName);
    Bundle unzipToDir(in ParcelFileDescriptor pfd, String targetDir);
}
