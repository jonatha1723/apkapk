package com.example

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable

@Serializable
data class AdminRecord(
    val id: Int = 1,
    val device_id: String
)

object AdminService {
    suspend fun verifyAndBindAdminDevice(deviceId: String): Pair<Boolean, String?> {
        return try {
            val records = SupabaseManager.client.postgrest["admins"]
                .select(Columns.ALL)
                .decodeList<AdminRecord>()

            if (records.isEmpty()) {
                // First admin login, bind this device
                SupabaseManager.client.postgrest["admins"].insert(AdminRecord(id = 1, device_id = deviceId))
                Pair(true, null)
            } else {
                // Check if device matches
                val adminRecord = records.first()
                if (adminRecord.device_id == deviceId) {
                    Pair(true, null)
                } else {
                    Pair(false, "Device ID não confere.")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // If table doesn't exist or error, to be safe deny or allow? 
            // Better tell user to create the table. We will show error.
            Pair(false, e.message ?: e.toString())
        }
    }
}
