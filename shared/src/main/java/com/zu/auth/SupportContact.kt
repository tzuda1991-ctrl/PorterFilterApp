package com.zu.auth

import com.google.firebase.firestore.FirebaseFirestore

data class SupportContact(val phone: String, val whatsapp: String) {
    companion object {
        fun document() = FirebaseFirestore.getInstance().collection("app_config").document("support")
    }
}
