package com.zu.auth

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore

object DriverNumber {
    private const val FIRST = 211126L

    fun label(number: Long): String? = number.takeIf { it >= FIRST }?.let { "zupro$it" }

    fun allocate(uid: String): Task<Long> {
        val firestore = FirebaseFirestore.getInstance()
        val driver = firestore.collection("users").document(uid)
        val counter = firestore.collection("counters").document("driver_numbers")
        return firestore.runTransaction { transaction ->
            val profile = transaction.get(driver)
            check(profile.exists() && profile.getString("status") != "deleted")
            profile.getLong("driverNumber")?.let { return@runTransaction it }

            val previous = transaction.get(counter).getLong("lastNumber") ?: (FIRST - 1)
            val assigned = previous + 1
            transaction.update(driver, "driverNumber", assigned)
            transaction.set(counter, mapOf("lastNumber" to assigned, "lastUid" to uid))
            assigned
        }
    }
}
