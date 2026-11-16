package app.hearth.domain

import java.io.File
import kotlin.test.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The copies of the shared `Spec` folder in this repository's test resources are the folder as it stands. The folder
 * lives in the iOS repository, beside this one (`../Hearth/Spec`); where it is not there, as on a build server
 * that checks out this repository alone, there is nothing to compare with and the test is skipped.
 */
class SpecCopyTests {

    @Test
    fun `the copied vectors and wire examples match the Spec folder`() {
        // Tests run in the module's own folder, so the repository is the one above it.
        val repository = File(System.getProperty("user.dir")).absoluteFile.parentFile
        val spec = File(repository, "../Hearth/Spec").canonicalFile
        assumeTrue("No Spec folder at $spec", File(spec, "vectors").isDirectory && File(spec, "wire").isDirectory)
        val copies = listOf(
            "vectors" to "domain/src/test/resources/spec/vectors",
            "wire" to "domain/src/test/resources/spec/wire",
            "wire" to "data/src/test/resources/spec/wire",
        )
        copies.forEach { (source, copy) ->
            val original = contents(File(spec, source))
            val copied = contents(File(repository, copy))

            assertEquals(original.keys, copied.keys, "The files of $copy differ from Spec: run tools/sync-spec.sh")
            original.forEach { (name, text) -> assertEquals(text, copied[name], "$copy/$name differs from Spec: run tools/sync-spec.sh") }
        }
    }

    private fun contents(folder: File): Map<String, String> =
        folder.listFiles { file -> file.extension == "json" }.orEmpty().associate { it.name to it.readText() }
}
