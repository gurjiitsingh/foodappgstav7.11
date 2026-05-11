package com.it10x.foodappgstav7_11.printer.queue

import android.util.Log
import com.it10x.foodappgstav7_11.data.printqueue.PrintQueueEntity
import com.it10x.foodappgstav7_11.data.PrinterRole
import com.it10x.foodappgstav7_11.data.printqueue.PrintQueueDao

import com.it10x.foodappgstav7_11.printer.PrinterManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class PrintQueueManager(
    private val dao: PrintQueueDao,
    private val printerManager: PrinterManager
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())


    private val channels = mutableMapOf<PrinterRole, Channel<PrintQueueEntity>>()

    init {
        PrinterRole.values().forEach { role ->
            val channel = Channel<PrintQueueEntity>(Channel.UNLIMITED)
            channels[role] = channel
            startWorker(channel)
        }

       // Log.e("QUEUE_INIT", "🔥 PrintQueueManager CREATED ${System.currentTimeMillis()}")

        scope.launch {
            loadPendingJobs()
        }
    }

    suspend fun enqueue(role: PrinterRole, text: String) {

        val job = PrintQueueEntity(
            id = UUID.randomUUID().toString(),
            role = role.name,
            text = text,
            status = "PENDING",
            retryCount = 0,
            createdAt = System.currentTimeMillis()
        )

        dao.insert(job)

        channels[role]?.send(job)   // ✅ ROLE BASED
    }

    private fun startWorker(channel: Channel<PrintQueueEntity>) {
        scope.launch {
            for (job in channel) {
                processJob(job)
            }
        }
    }


    private suspend fun processJob3(job: PrintQueueEntity) {

        val role = PrinterRole.valueOf(job.role)

        dao.updateStatus(job.id, "PRINTING", job.retryCount)

        Log.d("PRINT_QUEUE", "Printing job ${job.id}")

        val success = suspendCancellableCoroutine<Boolean> { cont ->
            printerManager.printText(role, job.text) {
                if (cont.isActive) cont.resume(it)
            }
        }

        if (success) {
            dao.delete(job.id)
            Log.d("PRINT_QUEUE", "DONE ${job.id}")

        } else {

            val retry = job.retryCount + 1

            Log.e("PRINT_QUEUE", "FAILED ${job.id} retry=$retry")

            if (retry >= 2) {
                dao.delete(job.id)
                Log.e("PRINT_QUEUE", "GIVE UP ${job.id}")
                return
            }

            dao.updateStatus(job.id, "PENDING", retry)

            delay(2000)

            // ✅ reuse same job (NO copy)
            channels[role]?.send(job)
        }
    }

    private suspend fun processJob2(job: PrintQueueEntity) {

        val role = PrinterRole.valueOf(job.role)

        dao.updateStatus(job.id, "PRINTING", job.retryCount)

        Log.d("PRINT_QUEUE", "Printing job ${job.id}")

        val success = suspendCancellableCoroutine<Boolean> { cont ->

            printerManager.printText(
                role,
                job.text
            ) {
                if (cont.isActive) cont.resume(it)
            }
        }

        if (success) {
            dao.delete(job.id)
            Log.d("PRINT_QUEUE", "DONE ${job.id}")

        } else {

            val retry = job.retryCount + 1

            Log.e("PRINT_QUEUE", "FAILED ${job.id} retry=$retry")

            if (retry >= 2) {
                // ❌ avoid infinite retry
                dao.delete(job.id)
                Log.e("PRINT_QUEUE", "GIVE UP ${job.id}")
                return
            }

            // 🔁 retry later
            dao.updateStatus(job.id, "PENDING", retry)

            delay(2000)

            channels[role]?.send(job.copy(retryCount = retry))
        }
    }

    private suspend fun processJob(job: PrintQueueEntity) {

        dao.updateStatus(job.id, "PRINTING", job.retryCount)

        Log.d("PRINT_QUEUE", "Printing job ${job.id}")

        suspendCancellableCoroutine<Unit> { cont ->

            printerManager.printText(
                PrinterRole.valueOf(job.role),
                job.text
            ) {
                if (cont.isActive) cont.resume(Unit)
            }
        }

        // ✅ ALWAYS mark success after first attempt
        dao.delete(job.id)

        Log.d("PRINT_QUEUE", "DONE ${job.id}")
    }

    private suspend fun loadPendingJobs() {
        val jobs = dao.getPending()

        jobs.forEach { job ->
            val role = PrinterRole.valueOf(job.role)
            channels[role]?.send(job)
        }
    }
}