package com.sza.streamsplayer.data.exchange.drive

/** An in-memory app folder: ids are handed out in order, `modifiedTime` follows [clock], failures are scripted. */
class FakeDriveFilesApi(private val clock: () -> Long) : DriveFilesApi {

    class Stored(val id: String, var name: String, var content: String, var modifiedTimeMs: Long)

    val files = linkedMapOf<String, Stored>()
    val calls = mutableListOf<String>()

    /** Thrown by the next write call (`create`, `update`) and by every one after it while [failWritesAlways] holds. */
    var nextWriteFailure: DriveException? = null
    var failWritesAlways = false
    var failDeletes = false
    var failEverything: DriveException? = null

    private var counter = 0

    fun put(name: String, content: String, modifiedTimeMs: Long = clock()): String {
        val id = "file-${++counter}"
        files[id] = Stored(id, name, content, modifiedTimeMs)
        return id
    }

    fun byName(name: String): Stored? = files.values.firstOrNull { it.name == name }

    fun namesWithPrefix(prefix: String): List<String> = files.values.map { it.name }.filter { it.startsWith(prefix) }

    override suspend fun list(namePrefix: String): DriveListing {
        calls += "list"
        failEverything?.let { throw it }
        return DriveListing(
            files.values.filter { it.name.startsWith(namePrefix) }.map { DriveFileInfo(it.id, it.name, it.modifiedTimeMs) },
            clock(),
        )
    }

    override suspend fun read(fileId: String): String? {
        calls += "read"
        failEverything?.let { throw it }
        return files[fileId]?.content
    }

    override suspend fun create(name: String, content: String): String {
        calls += "create"
        failEverything?.let { throw it }
        scriptedWriteFailure()?.let { throw it }
        return put(name, content)
    }

    override suspend fun update(fileId: String, content: String) {
        calls += "update"
        failEverything?.let { throw it }
        scriptedWriteFailure()?.let { throw it }
        val stored = files[fileId] ?: throw DriveException.NotFound()
        stored.content = content
        stored.modifiedTimeMs = clock()
    }

    override suspend fun delete(fileId: String) {
        calls += "delete"
        if (failDeletes) throw DriveException.Server(503)
        failEverything?.let { throw it }
        files.remove(fileId)
    }

    private fun scriptedWriteFailure(): DriveException? {
        val failure = nextWriteFailure
        if (!failWritesAlways) nextWriteFailure = null
        return failure
    }
}
