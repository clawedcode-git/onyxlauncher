package com.onyxlauncher.data.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object SettingsSerializer : Serializer<SettingsProto> {
    override val defaultValue: SettingsProto = SettingsProto.newBuilder()
        .setHomeColumns(5)
        .setHomeRows(5)
        .setDockSlots(5)
        .setIconSizeDp(56)
        .setShowLabels(true)
        .setLabelSizeSp(11)
        .setThemeMode(2)        // ThemeMode.SYSTEM ordinal
        .setUseDynamicColor(true)
        .setSwipeUp(1)          // GestureAction.OPEN_DRAWER
        .setSwipeDown(3)        // GestureAction.NOTIFICATIONS
        .setUseTimeOfDayColor(true)
        .setAnimationScale(1f)
        .setShowStatusBar(true)
        .build()

    override suspend fun readFrom(input: InputStream): SettingsProto =
        try {
            SettingsProto.parseFrom(input)
        } catch (e: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto settings", e)
        }

    override suspend fun writeTo(t: SettingsProto, output: OutputStream) =
        t.writeTo(output)
}
