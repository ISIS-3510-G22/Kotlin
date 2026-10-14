package com.example.plansync.data

/**
 * Model layer — sealed class for write operation outcomes.
 *
 * Used by repositories that must support offline writes (eventual connectivity).
 * ViewModels map WriteResult to UI state so the UI can inform the user whether
 * their action was committed immediately or queued for when connectivity returns.
 *
 * Design pattern: Sealed class / algebraic data type — exhaustive when-expressions
 * in the ViewModel guarantee every outcome is handled.
 */
sealed class WriteResult {
    /** Write committed to Firestore server immediately. */
    data object Committed : WriteResult()

    /**
     * Device was offline; Firestore SDK queued the write locally.
     * Will be synced automatically once connectivity is restored.
     */
    data object Queued : WriteResult()

    /** Write failed with a non-recoverable error. */
    data class Failed(val error: Throwable) : WriteResult()
}
