package com.example.plansync.data

// Returned by repositories so ViewModels can surface offline feedback to the UI.
sealed class WriteResult {
    data object Committed : WriteResult()
    data object Queued : WriteResult()   // offline — SDK will sync when back online
    data class Failed(val error: Throwable) : WriteResult()
}
