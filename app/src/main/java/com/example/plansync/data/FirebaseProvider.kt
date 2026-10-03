package com.example.plansync.data

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage

object FirebaseProvider {
    val firestore: FirebaseFirestore by lazy { Firebase.firestore }
    val auth: FirebaseAuth by lazy { Firebase.auth }
    val storage: FirebaseStorage by lazy { Firebase.storage }
}
