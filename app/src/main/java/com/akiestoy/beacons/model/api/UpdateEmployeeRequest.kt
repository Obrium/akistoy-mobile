package com.akiestoy.beacons.model.api

import com.google.gson.annotations.SerializedName

/**
 * Request para actualizar empleado
 */
data class UpdateEmployeeRequest(
    @SerializedName("name")
    val name: String,

    @SerializedName("email")
    val email: String,

    @SerializedName("active")
    val active: Boolean,

    @SerializedName("consentTracking")
    val consentTracking: Boolean
)

