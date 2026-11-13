package app.hearth.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The picture models the app knows how to drive through ComfyUI, each with the settings its makers recommend. */
enum class ImageEngineDto(
    /** The file that holds the model on the server. The engine is offered only when the server has it. */
    val modelFile: String,
    /** Whether the model comes as one file with its text reader and decoder inside. */
    internal val isCheckpoint: Boolean,
) {
    Z_IMAGE_TURBO("z_image_turbo_int8_convrot.safetensors", isCheckpoint = false),
    KREA2_TURBO("krea2_turbo_fp8_scaled.safetensors", isCheckpoint = false),
    JUGGERNAUT_XL("juggernautXL_v9.safetensors", isCheckpoint = true),
}

/** The files that picture editing and enlarging need on the server. */
object ImageToolFile {
    const val EDIT_MODEL = "flux-2-klein-4b.safetensors"
    const val ENLARGE_MODEL = "RealESRGAN_x4plus.safetensors"
    internal const val EDIT_TEXT_ENCODER = "qwen_3_4b_fp4_flux2.safetensors"
    internal const val EDIT_VAE = "flux2-vae.safetensors"
}

/**
 * Builds the node graphs ComfyUI runs. A graph is a dictionary of numbered nodes, each naming its kind and inputs;
 * an input that comes from another node is written as that node's number and output slot.
 *
 * Every graph ends in a preview, not a save, so the finished picture sits in the server's temporary folder and is
 * gone when the server next restarts, instead of piling up in its output folder.
 */
internal object ImageGraph {

    /** A picture from words alone. */
    fun create(engine: ImageEngineDto, prompt: String, width: Int, height: Int, seed: Int): JsonObject {
        val nodes = mutableMapOf<String, JsonElement>()
        var model = link("10", 0)
        var clip = link("11", 0)
        var vae = link("12", 0)
        var latentKind = "EmptyLatentImage"
        val sampling = mutableMapOf<String, Any>("steps" to 8, "cfg" to 1.0, "sampler_name" to "euler", "scheduler" to "simple")
        when (engine) {
            ImageEngineDto.Z_IMAGE_TURBO -> {
                nodes["10"] = node("UNETLoader", "unet_name" to engine.modelFile, "weight_dtype" to "default")
                nodes["11"] = node("CLIPLoader", "clip_name" to "qwen_3_4b_fp8_mixed.safetensors", "type" to "lumina2", "device" to "default")
                nodes["12"] = node("VAELoader", "vae_name" to "ae.safetensors")
                nodes["13"] = node("ModelSamplingAuraFlow", "model" to model, "shift" to 3.0)
                model = link("13", 0)
                latentKind = "EmptySD3LatentImage"
                sampling["sampler_name"] = "res_multistep"
            }
            ImageEngineDto.KREA2_TURBO -> {
                nodes["10"] = node("UNETLoader", "unet_name" to engine.modelFile, "weight_dtype" to "default")
                nodes["11"] = node("CLIPLoader", "clip_name" to "qwen3vl_4b_fp8_scaled.safetensors", "type" to "krea2", "device" to "default")
                nodes["12"] = node("VAELoader", "vae_name" to "qwen_image_vae.safetensors")
                nodes["13"] = node("ModelSamplingFlux", "model" to model, "max_shift" to 1.15, "base_shift" to 0.5, "width" to width, "height" to height)
                model = link("13", 0)
            }
            ImageEngineDto.JUGGERNAUT_XL -> {
                nodes["10"] = node("CheckpointLoaderSimple", "ckpt_name" to engine.modelFile)
                clip = link("10", 1)
                vae = link("10", 2)
                sampling += mapOf("steps" to 30, "cfg" to 6.5, "sampler_name" to "dpmpp_2m", "scheduler" to "karras")
            }
        }
        nodes["2"] = node("CLIPTextEncode", "text" to prompt, "clip" to clip)
        // The fast models take no guidance, so what to avoid is left blank; the classic one is steered away from clutter.
        nodes["3"] = if (engine == ImageEngineDto.JUGGERNAUT_XL) {
            node("CLIPTextEncode", "text" to "text, watermark, low quality, deformed", "clip" to clip)
        } else {
            node("ConditioningZeroOut", "conditioning" to link("2", 0))
        }
        nodes["4"] = node(latentKind, "width" to width, "height" to height, "batch_size" to 1)
        sampling += mapOf(
            "model" to model, "positive" to link("2", 0), "negative" to link("3", 0), "latent_image" to link("4", 0), "seed" to seed, "denoise" to 1.0,
        )
        nodes["5"] = node("KSampler", *sampling.toList().toTypedArray())
        nodes["6"] = node("VAEDecode", "samples" to link("5", 0), "vae" to vae)
        nodes["7"] = node("PreviewImage", "images" to link("6", 0))
        return JsonObject(nodes)
    }

    /** An uploaded picture changed as the instruction asks, by FLUX.2 klein, at about a megapixel. */
    fun edit(imageName: String, instruction: String, seed: Int): JsonObject = JsonObject(
        mapOf(
            "1" to node("LoadImage", "image" to imageName),
            "2" to node("ImageScaleToTotalPixels", "image" to link("1", 0), "upscale_method" to "lanczos", "megapixels" to 1.0, "resolution_steps" to 16),
            "3" to node("GetImageSize", "image" to link("2", 0)),
            "10" to node("UNETLoader", "unet_name" to ImageToolFile.EDIT_MODEL, "weight_dtype" to "default"),
            "11" to node("CLIPLoader", "clip_name" to ImageToolFile.EDIT_TEXT_ENCODER, "type" to "flux2", "device" to "default"),
            "12" to node("VAELoader", "vae_name" to ImageToolFile.EDIT_VAE),
            "20" to node("CLIPTextEncode", "text" to instruction, "clip" to link("11", 0)),
            "21" to node("ConditioningZeroOut", "conditioning" to link("20", 0)),
            "22" to node("VAEEncode", "pixels" to link("2", 0), "vae" to link("12", 0)),
            // The picture is given to the model as a reference on both sides of its guidance.
            "23" to node("ReferenceLatent", "conditioning" to link("20", 0), "latent" to link("22", 0)),
            "24" to node("ReferenceLatent", "conditioning" to link("21", 0), "latent" to link("22", 0)),
            "30" to node("EmptyFlux2LatentImage", "width" to link("3", 0), "height" to link("3", 1), "batch_size" to 1),
            "31" to node("Flux2Scheduler", "steps" to 4, "width" to link("3", 0), "height" to link("3", 1)),
            "32" to node("KSamplerSelect", "sampler_name" to "euler"),
            "33" to node("RandomNoise", "noise_seed" to seed),
            "34" to node("CFGGuider", "model" to link("10", 0), "positive" to link("23", 0), "negative" to link("24", 0), "cfg" to 1.0),
            "35" to node(
                "SamplerCustomAdvanced",
                "noise" to link("33", 0),
                "guider" to link("34", 0),
                "sampler" to link("32", 0),
                "sigmas" to link("31", 0),
                "latent_image" to link("30", 0),
            ),
            "36" to node("VAEDecode", "samples" to link("35", 0), "vae" to link("12", 0)),
            "37" to node("PreviewImage", "images" to link("36", 0)),
        ),
    )

    /** An uploaded picture made four times larger each way, then brought down to the asked size if that is smaller. */
    fun enlarge(imageName: String, megapixels: Double): JsonObject = JsonObject(
        mapOf(
            "1" to node("LoadImage", "image" to imageName),
            "2" to node("UpscaleModelLoader", "model_name" to ImageToolFile.ENLARGE_MODEL),
            "3" to node("ImageUpscaleWithModel", "upscale_model" to link("2", 0), "image" to link("1", 0)),
            "4" to node("ImageScaleToTotalPixels", "image" to link("3", 0), "upscale_method" to "lanczos", "megapixels" to megapixels, "resolution_steps" to 1),
            "5" to node("PreviewImage", "images" to link("4", 0)),
        ),
    )

    /** An input taken from another node: that node's number and which of its outputs. */
    private fun link(node: String, slot: Int): JsonArray = JsonArray(listOf(JsonPrimitive(node), JsonPrimitive(slot)))

    /** One node: its kind and its inputs, each a link to another node, a number or a word. */
    private fun node(kind: String, vararg inputs: Pair<String, Any>): JsonObject {
        val values = inputs.associate { (name, value) ->
            name to when (value) {
                is JsonElement -> value
                is Number -> JsonPrimitive(value)
                else -> JsonPrimitive(value.toString())
            }
        }
        return JsonObject(mapOf("class_type" to JsonPrimitive(kind), "inputs" to JsonObject(values)))
    }
}
