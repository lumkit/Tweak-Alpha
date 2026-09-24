package io.github.lumkit.tweak.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import io.github.lumkit.tweak.common.utils.logD
import io.github.lumkit.tweak.common.utils.logW
import io.github.lumkit.tweak.server.ipc.TweakBinderContract
import io.github.lumkit.tweak.server.ipc.TweakServerConnection
import io.github.lumkit.tweak.server.ipc.isPrivilegedCaller

/**
 * 接收 TweakServer（app_process）投递的 Binder。
 * 特权进程通过 content call 方法 [TweakBinderContract.METHOD_SET_BINDER] 写入。
 */
class TweakBinderProvider : ContentProvider() {

    companion object {
        private const val TAG = "TweakBinderProvider"
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (method != TweakBinderContract.METHOD_SET_BINDER || extras == null) {
            return Bundle.EMPTY
        }
        val callingUid = android.os.Binder.getCallingUid()
        if (!isPrivilegedCaller(callingUid)) {
            logW("[BINDER] rejected caller uid=$callingUid", TAG)
            return Bundle.EMPTY
        }
        val binder: IBinder? = extras.getBinder(TweakBinderContract.EXTRA_BINDER)
        val alive = binder?.pingBinder() == true
        if (alive) {
            TweakServerConnection.binder = binder
            logD("[BINDER] received alive binder", TAG)
        } else {
            logW("[BINDER] empty or dead binder", TAG)
        }
        return Bundle.EMPTY
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
