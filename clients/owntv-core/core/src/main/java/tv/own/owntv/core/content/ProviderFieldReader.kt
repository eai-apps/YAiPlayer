// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.content

import android.util.JsonReader
import android.util.JsonToken

/** Provider credits and genres may be text, arrays of text, or arrays of named objects. */
internal fun readProviderField(reader: JsonReader): String? = when (reader.peek()) {
    JsonToken.STRING, JsonToken.NUMBER -> reader.nextString().trim().takeIf(String::isNotEmpty)
    JsonToken.BEGIN_ARRAY -> {
        val values = ArrayList<String>()
        reader.beginArray()
        while (reader.hasNext()) {
            when (reader.peek()) {
                JsonToken.STRING, JsonToken.NUMBER -> reader.nextString().trim().takeIf(String::isNotEmpty)?.let(values::add)
                JsonToken.BEGIN_OBJECT -> {
                    var name: String? = null
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val key = reader.nextName()
                        if (key in listOf("name", "title") && reader.peek() == JsonToken.STRING) name = reader.nextString()
                        else reader.skipValue()
                    }
                    reader.endObject()
                    name?.trim()?.takeIf(String::isNotEmpty)?.let(values::add)
                }
                else -> reader.skipValue()
            }
        }
        reader.endArray()
        values.distinct().joinToString(", ").takeIf(String::isNotEmpty)
    }
    else -> { reader.skipValue(); null }
}
