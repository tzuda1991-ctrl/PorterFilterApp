package com.zu.auth

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

object CityCatalog {
    fun document() = FirebaseFirestore.getInstance().collection("app_config").document("driver_cities")

    fun read(snapshot: DocumentSnapshot): List<String> {
        if (!snapshot.exists()) return AreaCatalog.cities
        return (snapshot.get("cities") as? List<*>)?.filterIsInstance<String>().orEmpty()
    }

    fun fetch(callback: (List<String>?) -> Unit) {
        document().get(Source.SERVER).addOnCompleteListener { task ->
            callback(if (task.isSuccessful) read(task.result) else null)
        }
    }
}
