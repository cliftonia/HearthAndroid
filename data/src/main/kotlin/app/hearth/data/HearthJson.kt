package app.hearth.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/** The JSON dialect of every service the app talks to: unknown keys ignored, absent values left out. */
// Leaving absent values out (`explicitNulls`) is still marked experimental by the library.
@OptIn(ExperimentalSerializationApi::class)
val hearthJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}
