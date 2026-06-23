package io.github.lumkit.tweak.common.shell

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.OutputStream
import java.nio.charset.Charset

/**
 * 输出监听器接口
 */
fun interface OutputListener {
    /**
     * 接收实时输出行
     * @param line 输出的一行内容
     */
    fun onOutputReceived(line: String)
}

/**
 * 复用Process工具类 - 协程版本
 * 通过setRuntime设置Process实例，支持协程并发控制和实时输出监听
 */
class KeepShell {
    private var p: Process? = null
    private var out: OutputStream? = null
    private var reader: BufferedReader? = null
    private var errorReaderJob: Job? = null
    private var monitorJob: Job? = null
    
    @Volatile
    private var currentIsIdle = true
    
    val isIdle: Boolean
        get() = currentIsIdle

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 配置参数
    private companion object {
        private const val LOCK_TIMEOUT = 10000L
        private const val INIT_TIMEOUT = 10000L
        private val START_TAG = "|SH>>|"
        private val END_TAG = "|<<SH|"
    }
    
    private val startTagBytes = "\necho '$START_TAG'\n".toByteArray(Charset.defaultCharset())
    private val endTagBytes = "\necho '$END_TAG'\n".toByteArray(Charset.defaultCharset())
    
    @Volatile
    private var enterLockTime = 0L

    /**
     * 设置Runtime进程实例
     * @param process 外部传入的Process实例（可以是root或普通shell）
     */
    fun setRuntime(process: Process) {
        runBlocking {
            mutex.withLock {
                tryExitInternal()
                p = process
                initializeStreams()
            }
        }
    }

    /**
     * 获取当前Runtime实例
     */
    fun getRuntime(): Process? = p

    /**
     * 初始化输入输出流和错误流监听
     */
    private suspend fun initializeStreams() = withContext(Dispatchers.IO) {
        try {
            val process = p ?: throw IllegalStateException("Process not initialized")
            
            out = process.outputStream
            reader = process.inputStream.bufferedReader()
            
            // 启动错误流监听协程
            errorReaderJob?.cancel()
            errorReaderJob = scope.launch {
                try {
                    val errorReader = process.errorStream.bufferedReader()
                    while (isActive) {
                        val line = errorReader.readLine() ?: break
                        Log.e("KeepShell-Error", line)
                    }
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        Log.e("KeepShell-ErrorReader", "Error stream reader failed: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("KeepShell-Init", "Failed to initialize streams: ${e.message}")
            throw e
        }
    }

    /**
     * 尝试退出命令行程序
     */
    fun tryExit() {
        runBlocking {
            mutex.withLock {
                tryExitInternal()
            }
        }
    }

    private fun tryExitInternal() {
        errorReaderJob?.cancel()
        errorReaderJob = null
        
        monitorJob?.cancel()
        monitorJob = null
        
        runCatching { out?.close() }
        runCatching { reader?.close() }
        runCatching { p?.destroy() }
        
        enterLockTime = 0L
        out = null
        reader = null
        p = null
        currentIsIdle = true
    }

    /**
     * 执行命令（同步）- 协程版本
     * @param cmd 要执行的命令
     * @return 命令输出结果
     */
    suspend fun doCmdSync(cmd: String): String = withContext(Dispatchers.IO) {
        // 检查锁超时
        if (mutex.isLocked && enterLockTime > 0 && 
            System.currentTimeMillis() - enterLockTime > LOCK_TIMEOUT) {
            Log.e("KeepShell-Lock", "Mutex timeout detected, resetting...")
            tryExit()
        }

        // 确保Process已初始化
        if (p == null) {
            throw IllegalStateException("Process not set. Call setRuntime() first.")
        }

        mutex.withLock {
            currentIsIdle = false
            enterLockTime = System.currentTimeMillis()

            try {
                executeCommand(cmd)
            } catch (e: Exception) {
                Log.e("KeepShell-Exec", "Command execution failed: ${e.message}")
                tryExitInternal()
                "error"
            } finally {
                enterLockTime = 0L
                currentIsIdle = true
            }
        }
    }

    /**
     * 执行命令并通过监听器实时接收输出（用于长时间运行的命令）
     * @param cmd 要执行的命令（如 "update_engine_client --follow"）
     * @param listener 输出监听器，每行输出都会回调
     * @return 可取消的Job，调用cancel()可停止监听
     */
    fun doCmdWithListener(cmd: String, listener: OutputListener): Job {
        if (p == null) {
            throw IllegalStateException("Process not set. Call setRuntime() first.")
        }

        // 取消之前的监听任务
        monitorJob?.cancel()
        
        monitorJob = scope.launch {
            try {
                currentIsIdle = false
                
                val outputStream = out ?: throw IllegalStateException("OutputStream not initialized")
                val inputReader = reader ?: throw IllegalStateException("Reader not initialized")
                
                // 发送命令（不使用标记，直接发送原始命令）
                outputStream.apply {
                    write("${cmd}\n".toByteArray(Charset.defaultCharset()))
                    flush()
                }

                // 持续读取输出直到任务被取消
                while (isActive) {
                    val line = withContext(Dispatchers.IO) {
                        inputReader.readLine()
                    } ?: break
                    
                    // 实时回调每一行输出
                    withContext(Dispatchers.Main) {
                        listener.onOutputReceived(line)
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e("KeepShell-Monitor", "Monitor command failed: ${e.message}")
                    withContext(Dispatchers.Main) {
                        listener.onOutputReceived("Error: ${e.message}")
                    }
                }
            } finally {
                currentIsIdle = true
            }
        }
        
        return monitorJob!!
    }

    /**
     * 执行命令并通过Flow实时接收输出（Kotlin风格的响应式API）
     * @param cmd 要执行的命令
     * @return Flow<String> 每行输出作为Flow元素发射
     */
    fun doCmdAsFlow(cmd: String): Flow<String> = flow {
        if (p == null) {
            throw IllegalStateException("Process not set. Call setRuntime() first.")
        }

        try {
            currentIsIdle = false
            
            val outputStream = out ?: throw IllegalStateException("OutputStream not initialized")
            val inputReader = reader ?: throw IllegalStateException("Reader not initialized")
            
            // 发送命令
            withContext(Dispatchers.IO) {
                outputStream.apply {
                    write("${cmd}\n".toByteArray(Charset.defaultCharset()))
                    flush()
                }
            }

            // 持续发射输出行
            while (currentCoroutineContext().isActive) {
                val line = withContext(Dispatchers.IO) {
                    inputReader.readLine()
                } ?: break
                
                emit(line)
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                Log.e("KeepShell-Flow", "Flow command failed: ${e.message}")
                emit("Error: ${e.message}")
            }
        } finally {
            currentIsIdle = true
        }
    }

    /**
     * 停止当前的监听任务
     */
    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        currentIsIdle = true
    }

    /**
     * 执行命令的核心逻辑
     */
    private suspend fun executeCommand(cmd: String): String = coroutineScope {
        val outputStream = out ?: throw IllegalStateException("OutputStream not initialized")
        val inputReader = reader ?: throw IllegalStateException("Reader not initialized")
        
        // 发送命令
        outputStream.apply {
            write(startTagBytes)
            write(cmd.toByteArray(Charset.defaultCharset()))
            write(endTagBytes)
            flush()
        }

        // 读取输出
        val output = StringBuilder()
        var started = false

        while (isActive) {
            val line = withContext(Dispatchers.IO) {
                inputReader.readLine()
            } ?: break

            when {
                line.contains(END_TAG) -> {
                    output.append(line.substring(0, line.indexOf(END_TAG)))
                    break
                }
                line.contains(START_TAG) -> {
                    output.clear()
                    output.append(line.substring(line.indexOf(START_TAG) + START_TAG.length))
                    started = true
                }
                started -> {
                    output.append(line)
                    output.append("\n")
                }
            }
        }

        output.toString().trim()
    }

    /**
     * 阻塞式执行命令（为兼容旧代码提供）
     */
    fun doCmdSyncBlocking(cmd: String): String = runBlocking {
        doCmdSync(cmd)
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        scope.cancel()
        tryExit()
    }
}
