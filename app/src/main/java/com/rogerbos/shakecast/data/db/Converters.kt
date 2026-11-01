package com.rogerbos.shakecast.data.db

import androidx.room.TypeConverter
import com.rogerbos.shakecast.domain.model.AdSource
import com.rogerbos.shakecast.domain.model.DownloadState

class Converters {
    @TypeConverter
    fun downloadStateToString(value: DownloadState): String = value.name

    @TypeConverter
    fun stringToDownloadState(value: String): DownloadState = DownloadState.valueOf(value)

    @TypeConverter
    fun adSourceToString(value: AdSource): String = value.name

    @TypeConverter
    fun stringToAdSource(value: String): AdSource = AdSource.valueOf(value)
}
