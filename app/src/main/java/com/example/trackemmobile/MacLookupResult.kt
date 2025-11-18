package com.example.trackemmobile

import com.google.gson.annotations.SerializedName

data class MacLookupResult(
    @SerializedName("success") val success: Boolean,
    @SerializedName("found") val found: Boolean,
    @SerializedName("macPrefix") val macPrefix: String?,
    @SerializedName("company") val companyName: String?,
    @SerializedName("address") val companyAddress: String?,
    @SerializedName("country") val countryCode: String?,
    @SerializedName("blockStart") val blockStart: String?,
    @SerializedName("blockEnd") val blockEnd: String?,
    @SerializedName("blockSize") val blockSize: Long?,
    @SerializedName("blockType") val assignmentBlockSize: String?,
    @SerializedName("updated") val updated: String?,
    @SerializedName("isRand") val isRand: Boolean?,
    @SerializedName("isPrivate") val isPrivate: Boolean?
)
