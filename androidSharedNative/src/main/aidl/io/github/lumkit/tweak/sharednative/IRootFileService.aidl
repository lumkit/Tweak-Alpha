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
    Bundle length(String path);
    Bundle readCpuCycles(int coreIndex);
    Bundle listInstalledApps();
    Bundle getInstalledApp(String packageName);
    Bundle unzipToDir(in ParcelFileDescriptor pfd, String targetDir);
    Bundle unzipPathToDir(String sourcePath, String targetDir);
    /** 在特权进程内异步启动命令（不阻塞等待），用于拉起独立 TweakServer 进程。 */
    Bundle execDetached(String command);
    /** 在当前特权进程内嵌入启动 TweakServer（Shizuku file_service 推荐路径）。 */
    Bundle startTweakServerEmbedded(String packageName);
    Bundle stopTweakServerEmbedded();
}
