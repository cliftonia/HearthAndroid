package app.hearth.domain

import app.hearth.data.HttpError
import java.io.IOException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Test

/** One sync from end to end, between devices kept in memory and a hub with the real hub's rules. */
class SyncInteractorTests {

    @Test
    fun `a first sync on a device that has data merges both ways`() = runTest {
        val hub = FakeHub()
        val theirs = SyncSamples.conversation("From the phone")
        hub.seed(SyncItemKey.conversation(theirs.id), at = 500_000, body = SyncSamples.body(theirs))
        val device = SyncDevice(hub)
        device.conversations.save(SyncSamples.conversation("From the tablet"))

        device.syncNow()

        assertEquals(setOf("From the phone", "From the tablet"), device.conversations.conversations.value.map { it.title }.toSet())
        assertEquals(2, hub.all.size)
        // Nothing more to send once both sides hold the same.
        val putsBefore = hub.puts.size
        device.syncNow()
        assertEquals(putsBefore, hub.puts.size)
    }

    @Test
    fun `a chat made on one device reaches the other, and a change made there comes back`() = runTest {
        val hub = FakeHub()
        val tablet = SyncDevice(hub, now = 1_000_000)
        val phone = SyncDevice(hub, now = 2_000_000)
        val chat = SyncSamples.conversation("Tides", listOf(Message(role = MessageRole.USER, text = "When?")))
        tablet.conversations.save(chat)
        tablet.syncNow()

        phone.syncNow()
        assertEquals(listOf("When?"), phone.conversations.conversation(chat.id)?.messages?.map { it.text })

        phone.conversations.rename(chat.id, "Tide times")
        phone.conversations.setPinned(true, chat.id)
        phone.syncNow()
        tablet.syncNow()

        val arrived = assertNotNull(tablet.conversations.conversation(chat.id))
        assertEquals("Tide times" to true, arrived.title to arrived.isPinned)
        assertTrue(tablet.appliedRemotely.any { SyncItemKey.conversation(chat.id) in it })
        // The activity time travels as it is; a rename does not change it.
        assertEquals(SyncSamples.CREATED, arrived.updatedAt)
    }

    @Test
    fun `the newer copy wins whichever side holds it`() = runTest {
        val hub = FakeHub()
        val device = SyncDevice(hub, now = 1_000_000)
        val older = SyncSamples.conversation("Local")
        val newer = SyncSamples.conversation("Remote newer")
        device.conversations.save(older)
        device.conversations.save(newer.copy(title = "Local older"))
        // The hub's copy of `older` is from before the device changed it; its copy of `newer` from after.
        hub.seed(SyncItemKey.conversation(older.id), at = 10, body = SyncSamples.body(older.copy(title = "Remote older")))
        hub.seed(SyncItemKey.conversation(newer.id), at = 5_000_000, body = SyncSamples.body(newer))

        device.syncNow()

        assertEquals("Local", device.conversations.conversation(older.id)?.title)
        assertEquals("Remote newer", device.conversations.conversation(newer.id)?.title)
        assertEquals("Local", SyncSamples.conversation(hub.envelope(SyncItemKind.CONVERSATION, older.id.raw)).title)
    }

    @Test
    fun `a deletion here goes to the hub as a tombstone, and one from the hub deletes here`() = runTest {
        val hub = FakeHub()
        val device = SyncDevice(hub)
        val mine = SyncSamples.conversation("Mine")
        val theirs = SyncSamples.conversation("Theirs")
        device.conversations.save(mine)
        device.conversations.save(theirs)
        device.syncNow()

        device.conversations.delete(mine.id)
        hub.seed(SyncItemKey.conversation(theirs.id), at = 9_000_000, deleted = true)
        device.syncNow()

        assertTrue(device.conversations.conversations.value.isEmpty())
        assertEquals(true, hub.envelope(SyncItemKind.CONVERSATION, mine.id.raw)?.deleted)
        assertTrue(device.sync.journal.tombstones.isEmpty())
    }

    @Test
    fun `a project deleted here lets its chats go first, and the other device keeps them`() = runTest {
        val hub = FakeHub()
        val tablet = SyncDevice(hub, now = 1_000_000)
        val phone = SyncDevice(hub, now = 2_000_000)
        val project = Project(name = "Garden")
        tablet.projects.save(project)
        val chat = SyncSamples.conversation("Beds")
        tablet.conversations.save(chat)
        tablet.conversations.setProject(project.id, chat.id)
        tablet.syncNow()
        phone.syncNow()
        assertEquals(project.id, phone.conversations.conversation(chat.id)?.projectId)

        ProjectInteractor(phone.projects, phone.conversations).delete(project.id)
        phone.syncNow()
        tablet.syncNow()

        assertTrue(tablet.projects.projects.value.isEmpty())
        assertEquals(listOf<ProjectId?>(null), tablet.conversations.conversations.value.map { it.projectId })
        assertEquals(true, hub.envelope(SyncItemKind.PROJECT, project.id.raw)?.deleted)
    }

    @Test
    fun `a project deleted elsewhere keeps its chats here, without sending them`() = runTest {
        val hub = FakeHub()
        val device = SyncDevice(hub)
        val project = Project(name = "Garden")
        device.projects.save(project)
        val chat = SyncSamples.conversation("Beds")
        device.conversations.save(chat)
        device.conversations.setProject(project.id, chat.id)
        device.syncNow()
        val putsBefore = hub.puts.size

        hub.seed(SyncItemKey.project(project.id), at = 9_000_000, deleted = true)
        device.syncNow()

        assertTrue(device.projects.projects.value.isEmpty())
        assertEquals(listOf<ProjectId?>(null), device.conversations.conversations.value.map { it.projectId })
        assertEquals(putsBefore, hub.puts.size)
    }

    @Test
    fun `private chats never reach the hub, not even as a deletion`() = runTest {
        val hub = FakeHub()
        val device = SyncDevice(hub)
        val secret = Conversation(title = "Secret", messages = listOf(Message(role = MessageRole.USER, text = "Hush")), isPrivate = true)
        device.conversations.save(secret)
        device.conversations.rename(secret.id, "Still secret")
        device.conversations.setPinned(true, secret.id)
        device.syncNow()
        device.conversations.delete(secret.id)
        device.syncNow()

        assertTrue(hub.all.isEmpty())
        assertTrue(device.reported.isEmpty())
        assertTrue(device.sync.journal.tombstones.isEmpty())
    }

    @Test
    fun `a private chat here is never replaced by one from the hub with the same name`() = runTest {
        val hub = FakeHub()
        val device = SyncDevice(hub)
        val secret = Conversation(title = "Secret", messages = listOf(Message(role = MessageRole.USER, text = "Hush")), isPrivate = true)
        device.conversations.save(secret)
        hub.seed(SyncItemKey.conversation(secret.id), at = 9_000_000, body = SyncSamples.body(secret.copy(title = "Overwritten", isPrivate = false)))
        device.syncNow()
        hub.seed(SyncItemKey.conversation(secret.id), at = 9_500_000, deleted = true)
        device.syncNow()

        assertEquals("Secret", device.conversations.conversation(secret.id)?.title)
        assertEquals(true, device.conversations.conversation(secret.id)?.isPrivate)
    }

    @Test
    fun `a chat whose reply is streaming is neither replaced nor sent until it is finished`() = runTest {
        val hub = FakeHub()
        val device = SyncDevice(hub)
        val chat = SyncSamples.conversation("Open")
        device.conversations.save(chat)
        device.syncNow()
        hub.seed(SyncItemKey.conversation(chat.id), at = 9_000_000, body = SyncSamples.body(chat.copy(title = "Renamed elsewhere")))
        device.busy = setOf(chat.id)

        device.syncNow()
        assertEquals("Open", device.conversations.conversation(chat.id)?.title)
        assertTrue(device.sync.journal.cursor < hub.latestSeq)

        device.busy = emptySet()
        device.syncNow()
        assertEquals("Renamed elsewhere", device.conversations.conversation(chat.id)?.title)
        assertEquals(hub.latestSeq, device.sync.journal.cursor)
    }

    @Test
    fun `a busy chat changed here is not sent until it is finished`() = runTest {
        val hub = FakeHub()
        val device = SyncDevice(hub)
        val chat = SyncSamples.conversation("Open")
        device.conversations.save(chat)
        device.busy = setOf(chat.id)

        device.syncNow()
        assertTrue(hub.all.isEmpty())

        device.busy = emptySet()
        device.syncNow()
        assertEquals(1, hub.all.size)
    }

    @Test
    fun `a 409 means the hub's newer copy is taken`() = runTest {
        val hub = FakeHub()
        val device = SyncDevice(hub)
        val chat = SyncSamples.conversation("Mine")
        device.conversations.save(chat)
        // Another device writes a later copy between this device's pull and its push.
        hub.beforePut = { key ->
            hub.beforePut = null
            hub.seed(key, at = 9_000_000, body = SyncSamples.body(chat.copy(title = "Theirs, later")))
        }

        device.syncNow()

        assertEquals("Theirs, later", device.conversations.conversation(chat.id)?.title)
        assertEquals(9_000_000L, device.sync.journal.seen[SyncItemKey.conversation(chat.id)]?.milliseconds)
    }

    @Test
    fun `changes come in pages until there are no more`() = runTest {
        val hub = FakeHub()
        hub.pageLimit = 2
        (1..5).forEach { number ->
            val chat = SyncSamples.conversation("Chat $number")
            hub.seed(SyncItemKey.conversation(chat.id), at = number.toLong(), body = SyncSamples.body(chat))
        }
        val device = SyncDevice(hub)

        device.syncNow()

        assertEquals(5, device.conversations.conversations.value.size)
        assertEquals(5L, device.sync.journal.cursor)
    }

    @Test
    fun `a hub started afresh is synced with from the start`() = runTest {
        val hub = FakeHub()
        val device = SyncDevice(hub)
        device.conversations.save(SyncSamples.conversation("Kept"))
        device.syncNow()

        val fresh = FakeHub()
        fresh.hubId = "hub-two"
        device.hub = fresh
        device.syncNow()

        assertEquals("hub-two", device.sync.journal.hubId)
        assertEquals(1, fresh.all.size)
    }

    @Test
    fun `projects and memories sync, a memory getting a handle of this device's own`() = runTest {
        val hub = FakeHub()
        val device = SyncDevice(hub)
        device.memories.apply(MemoryRequest.Save("Likes tea"), isFromUser = true)
        val theirs = UUID.randomUUID().toString()
        hub.seed(SyncItemKey.memory(theirs), at = 5, body = SyncSamples.memoryBody("Lives in Brisbane"))
        val project = Project(name = "Garden", instructions = "Subtropical.")
        hub.seed(SyncItemKey.project(project.id), at = 6, body = SyncSamples.body(project))

        device.syncNow()

        assertEquals(listOf("m1", "m2"), device.memories.memories.value.map { it.id.raw })
        assertEquals(theirs, device.memories.memories.value.last().syncId)
        assertEquals("Subtropical.", device.projects.project(project.id)?.instructions)
        assertNotNull(hub.envelope(SyncItemKind.MEMORY, device.memories.memories.value[0].syncId))
    }

    @Test
    fun `a memory changed or forgotten on one device is changed or forgotten on the other`() = runTest {
        val hub = FakeHub()
        val tablet = SyncDevice(hub, now = 1_000_000)
        val phone = SyncDevice(hub, now = 2_000_000)
        tablet.memories.apply(MemoryRequest.Save("Lives in Brisbane"), isFromUser = true)
        tablet.memories.apply(MemoryRequest.Save("Likes tea"), isFromUser = true)
        tablet.syncNow()
        phone.syncNow()

        phone.memories.apply(MemoryRequest.Update(MemoryId("m1"), "Lives in Sydney"), isFromUser = true)
        phone.memories.apply(MemoryRequest.Delete(MemoryId("m2")), isFromUser = true)
        phone.syncNow()
        tablet.syncNow()

        assertEquals(listOf("Lives in Sydney"), tablet.memories.memories.value.map { it.text })
    }

    @Test
    fun `fields from a newer client are given back when the item is uploaded again`() = runTest {
        val hub = FakeHub()
        val chat = SyncSamples.conversation("Briefing", listOf(Message(role = MessageRole.USER, text = "News?")))
        val written = SyncSamples.body(chat)
        val message = written.getValue("messages").jsonArray[0].jsonObject
        val body = JsonObject(
            written + mapOf(
                "messages" to JsonArray(listOf(JsonObject(message + ("mood" to JsonPrimitive("curious"))))),
                "taskId" to JsonPrimitive("T1"),
                "schema" to JsonPrimitive(2),
            ),
        )
        hub.seed(SyncItemKey.conversation(chat.id), at = 5, body = body)
        val device = SyncDevice(hub)
        device.syncNow()

        device.conversations.rename(chat.id, "Morning briefing")
        device.syncNow()

        val uploaded = assertNotNull(hub.envelope(SyncItemKind.CONVERSATION, chat.id.raw)?.body)
        assertEquals(JsonPrimitive("Morning briefing"), uploaded["title"])
        assertEquals(JsonPrimitive("T1"), uploaded["taskId"])
        assertEquals(JsonPrimitive(2), uploaded["schema"])
        assertEquals(JsonPrimitive("curious"), uploaded.getValue("messages").jsonArray[0].jsonObject["mood"])
    }

    @Test
    fun `an item too large for the hub stays here and is counted, and the rest sync`() = runTest {
        val hub = FakeHub()
        val device = SyncDevice(hub)
        val large = SyncSamples.conversation("Pictures")
        device.conversations.save(large)
        device.conversations.save(SyncSamples.conversation("Words"))
        hub.tooLargeIds = setOf(large.id.raw)

        assertEquals(1, device.syncNow())
        assertEquals(1, hub.all.size)
    }

    @Test
    fun `a received item that cannot be read is passed over, and the rest arrive`() = runTest {
        val hub = FakeHub()
        val good = SyncSamples.conversation("Good")
        val bad = SyncSamples.conversation("Bad")
        hub.seed(SyncItemKey.conversation(bad.id), at = 5, body = JsonObject(mapOf("schema" to JsonPrimitive(1), "title" to JsonPrimitive("No date"))))
        hub.seed(SyncItemKey.conversation(good.id), at = 6, body = SyncSamples.body(good))
        val device = SyncDevice(hub)

        device.syncNow()

        assertEquals(listOf("Good"), device.conversations.conversations.value.map { it.title })
    }

    @Test
    fun `a hub that wants a token, or is down, says so plainly`() = runTest {
        val hub = FakeHub()
        val device = SyncDevice(hub)
        hub.failure = HttpError.Status(401, "")
        assertEquals(SyncFailureKind.TokenNeeded, assertFailsWith<SyncError> { device.syncNow() }.failure)
        device.sync.setToken("throwaway")
        assertEquals(SyncFailureKind.TokenRefused, assertFailsWith<SyncError> { device.syncNow() }.failure)
        hub.failure = HttpError.Status(500, "")
        assertEquals(SyncFailureKind.HubFailed(500), assertFailsWith<SyncError> { device.syncNow() }.failure)
        hub.failure = IOException("refused")
        assertEquals(SyncFailureKind.Unreachable, assertFailsWith<SyncError> { device.syncNow() }.failure)
        hub.failure = null
        device.syncNow()
        assertEquals("hub-one", device.sync.journal.hubId)
    }

    @Test
    fun `checking the hub says whether it answers and lets this device in`() = runTest {
        val hub = FakeHub()
        val device = SyncDevice(hub)
        assertEquals(SyncHubCheck.Reachable, device.interactor.check("https://hub.example", null))
        hub.failure = HttpError.Status(401, "")
        assertEquals(SyncHubCheck.Failed(SyncFailureKind.TokenNeeded), device.interactor.check("https://hub.example", null))
    }

    @Test
    fun `without an address nothing is asked`() = runTest {
        val device = SyncDevice(FakeHub())
        device.sync.update(SyncSettings())
        assertEquals(SyncFailureKind.NotConfigured, assertFailsWith<SyncError> { device.syncNow() }.failure)
    }

    @Test
    fun `the journal is kept across launches`() = runTest {
        val hub = FakeHub()
        val device = SyncDevice(hub)
        val chat = SyncSamples.conversation("Kept")
        device.conversations.save(chat)
        device.syncNow()
        device.conversations.delete(chat.id)

        val relaunched = SyncStore(device.storeProvider).also { it.load() }

        assertEquals(device.sync.journal.installId, relaunched.journal.installId)
        assertEquals(device.sync.journal.cursor, relaunched.journal.cursor)
        assertEquals("hub-one", relaunched.journal.hubId)
        assertEquals(device.sync.journal.tombstones, relaunched.journal.tombstones)
        assertEquals(device.sync.journal.seen, relaunched.journal.seen)
    }
}
