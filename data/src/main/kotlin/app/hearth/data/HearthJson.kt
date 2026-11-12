package app.hearth.data

import kotlinx.serialization.json.Json

/** The JSON dialect of every service the app talks to: unknown keys ignored, absent values left out. */
val hearthJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}
