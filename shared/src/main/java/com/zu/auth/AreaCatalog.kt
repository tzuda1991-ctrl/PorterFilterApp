package com.zu.auth

import android.content.Context
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

object AreaCatalog {
    private val defaults = linkedMapOf(
        "Bengaluru" to emptyArray<String>(),
        "Mumbai" to arrayOf(
            "Andheri", "Bandra", "Borivali", "Bhiwandi", "Goregaon", "Malad",
            "Kandivali", "Powai", "Kurla", "Ghatkopar", "Mulund", "Thane",
            "Airoli", "Vashi", "Turbhe", "Panvel", "Kalyan", "Dombivli",
            "Vasai", "Virar", "Dadar", "Worli", "Lower Parel", "Chembur", "Santacruz"
        ),
        "Delhi NCR" to arrayOf(
            "Okhla", "Bawana", "Narela", "Mayapuri", "Naraina", "Dwarka",
            "Rohini", "Janakpuri", "Karol Bagh", "Lajpat Nagar", "Saket",
            "Noida", "Greater Noida", "Gurugram", "Manesar", "Faridabad",
            "Ghaziabad", "Indirapuram", "Kundli", "Connaught Place"
        ),
        "Hyderabad" to arrayOf(
            "Gachibowli", "Madhapur", "HITEC City", "Kukatpally", "Balanagar",
            "Jeedimetla", "Uppal", "LB Nagar", "Secunderabad", "Banjara Hills",
            "Jubilee Hills", "Miyapur", "Patancheru", "Shamshabad", "Kompally", "Attapur"
        ),
        "Chennai" to arrayOf(
            "Guindy", "Ambattur", "Sriperumbudur", "Oragadam", "Anna Nagar",
            "T Nagar", "Velachery", "Tambaram", "Porur", "OMR", "Perungudi",
            "Poonamallee", "Madhavaram", "Chromepet", "Adyar"
        ),
        "Pune" to arrayOf(
            "Chakan", "Pimpri", "Chinchwad", "Bhosari", "Hinjewadi", "Hadapsar",
            "Kharadi", "Wagholi", "Wakad", "Baner", "Kothrud", "Viman Nagar",
            "Katraj", "Talegaon", "Ranjangaon"
        )
    )

    val cities: List<String> get() = defaults.keys.toList()

    fun defaultsFor(context: Context, city: String): Map<String, String> {
        if (city != "Bengaluru") return defaults[city].orEmpty().associateWith { "" }
        return context.assets.open("bengaluru-areas.tsv").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }
                .associate { it.substringBefore('\t') to it.substringAfter('\t', "") }
        }
    }

    fun document(city: String) = FirebaseFirestore.getInstance().collection("area_catalog").document(city)

    fun read(context: Context, city: String, snapshot: DocumentSnapshot): Map<String, String> {
        if (!snapshot.exists()) return defaultsFor(context, city)
        val values = snapshot.get("areas") as? Map<*, *> ?: return emptyMap()
        return values.entries.mapNotNull {
            val name = it.key as? String ?: return@mapNotNull null
            name to (it.value as? String).orEmpty()
        }.toMap()
    }
}
