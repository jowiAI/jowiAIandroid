package ai.workis.jowi.data

import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * FCM's two callbacks → [PushService] (ledger 45). A new registration token is kept and
 * `push/register/` goes out again while Bildirimler is on; a message that reaches the app
 * in the FOREGROUND only names its scopes to the watching screens — no banner, the change is
 * on screen (the same rule as iOS `willPresent`). In the background the system shows the
 * notification itself (Django sends notification + data); the tap lands in `MainActivity`.
 */
class WorkisMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = PushService.tokenArrived(token)

    override fun onMessageReceived(message: RemoteMessage) = PushService.messageArrived(message.data)
}

/** A Play-services `Task` as a suspend call (the FCM token), without the coroutines-play-services artifact. */
suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { t ->
        val e = t.exception
        when {
            e != null -> cont.resumeWithException(e)
            t.isCanceled -> cont.cancel()
            else -> cont.resume(t.result)
        }
    }
}
