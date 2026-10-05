package com.example.marvellobby

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.marvellobby.data.local.*
import com.example.marvellobby.data.repository.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountAndChatPersistenceTest {
    private fun context(): Context {
        val target=InstrumentationRegistry.getInstrumentation().targetContext
        return object: android.content.ContextWrapper(target) {
            override fun getApplicationContext(): Context=this
            override fun getFilesDir()=java.io.File(target.cacheDir,"account-chat-test-prefs").apply { mkdirs() }
        }
    }
    @Test fun chatsSurviveReopeningAndCannotBeReadOrDeletedByAnotherAccount() = runBlocking {
        val context=context();val name="chat-test-${System.nanoTime()}.db"
        var db=Room.databaseBuilder(context,MarvelDatabase::class.java,name).build()
        try {
            val prefs=PreferencesStore(context)
            val accounts=AccountRepository(db.archive(),prefs)
            accounts.register("First","first@chat.test","Password2026","Password2026")
            accounts.register("Second","second@chat.test","Password2026","Password2026")
            val snapshot=ChatSnapshot("test-conversation","Question",null,listOf(ChatMessage("user","Question"),ChatMessage("model","Answer")),emptyList())
            ChatRepository(db.archive()).save("first@chat.test",snapshot)
            db.close();db=Room.databaseBuilder(context,MarvelDatabase::class.java,name).build()
            val chats=ChatRepository(db.archive())
            assertEquals(2,chats.load("first@chat.test",snapshot.id)!!.messages.size)
            assertNull(chats.load("second@chat.test",snapshot.id))
            chats.delete("second@chat.test",snapshot.id)
            assertNotNull(chats.load("first@chat.test",snapshot.id))
            chats.save("second@chat.test",snapshot.copy(title="Attempted overwrite"))
            assertEquals("Question",chats.load("first@chat.test",snapshot.id)!!.title)
            chats.delete("first@chat.test",snapshot.id)
            assertTrue(chats.list("first@chat.test").isEmpty())
        } finally { db.close();context.deleteDatabase(name) }
    }
    @Test fun changingEmailAndPasswordMovesSavedDataAndRejectsOldCredentials() = runBlocking {
        val context=context();val db=Room.inMemoryDatabaseBuilder(context,MarvelDatabase::class.java).build()
        try {
            val accounts=AccountRepository(db.archive(),PreferencesStore(context))
            val originalProfile=accounts.register("First","old@account.test","Password2026","Password2026")
            accounts.register("Second","taken@account.test","Password2026","Password2026")
            val saved=StoredRecord().apply { owner="old@account.test";recordKey="CHARACTER:1";payload="{}";favorite=true }
            db.archive().save(saved)
            val chats=ChatRepository(db.archive())
            chats.save("old@account.test",ChatSnapshot("rename-chat","Hello",null,listOf(ChatMessage("user","Hello")),emptyList()))
            assertTrue(runCatching { accounts.updateDetails("old@account.test","First","new@account.test","wrong","NewPassword2026","NewPassword2026") }.isFailure)
            assertTrue(runCatching { accounts.updateDetails("old@account.test","First","taken@account.test","Password2026","","") }.isFailure)
            accounts.updateDetails("old@account.test","Updated","new@account.test","Password2026","NewPassword2026","NewPassword2026")
            assertNull(db.archive().account("old@account.test"))
            assertTrue(db.archive().records("old@account.test").isEmpty())
            assertTrue(db.archive().records("new@account.test").single().favorite)
            assertNull(chats.load("old@account.test","rename-chat"))
            assertNotNull(chats.load("new@account.test","rename-chat"))
            assertTrue(runCatching { accounts.login("new@account.test","Password2026") }.isFailure)
            assertEquals("Updated",accounts.login("new@account.test","NewPassword2026").name)
            assertEquals(originalProfile.username,accounts.profile("new@account.test")!!.username)
        } finally { db.close() }
    }
    @Test fun versionOneMigrationPreservesExistingAccountAndRecords() = runBlocking {
        val context=context();val name="migration-test-${System.nanoTime()}.db"
        val original=context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null)
        original.execSQL("CREATE TABLE accounts (email TEXT NOT NULL PRIMARY KEY,name TEXT NOT NULL,passwordHash TEXT NOT NULL,salt TEXT NOT NULL,avatar TEXT NOT NULL)")
        original.execSQL("CREATE TABLE records (owner TEXT NOT NULL,recordKey TEXT NOT NULL,payload TEXT NOT NULL,favorite INTEGER NOT NULL,viewedAt INTEGER NOT NULL,PRIMARY KEY(owner,recordKey))")
        original.execSQL("INSERT INTO accounts VALUES ('existing@test.local','Existing','hash','salt','')")
        original.execSQL("INSERT INTO records VALUES ('existing@test.local','CHARACTER:1','{}',1,123)")
        original.version=1;original.close()
        val db=Room.databaseBuilder(context,MarvelDatabase::class.java,name).addMigrations(MarvelDatabase.MIGRATION_1_2,MarvelDatabase.MIGRATION_2_3,MarvelDatabase.MIGRATION_3_4).build()
        try {
            assertEquals("Existing",db.archive().account("existing@test.local").name)
            assertTrue(com.example.marvellobby.data.model.Usernames.valid(db.archive().account("existing@test.local").username))
            assertTrue(db.archive().records("existing@test.local").single().favorite)
            ChatRepository(db.archive()).save("existing@test.local",ChatSnapshot("after-migration","Question",null,listOf(ChatMessage("user","Question")),emptyList()))
            assertEquals(1,db.archive().conversations("existing@test.local").size)
        } finally { db.close();context.deleteDatabase(name) }
    }
    @Test fun usernamesPersistAndDuplicateChangesLeaveAccountUntouched() = runBlocking<Unit> {
        val context=context();val name="username-test-${System.nanoTime()}.db"
        var db=Room.databaseBuilder(context,MarvelDatabase::class.java,name).build()
        try {
            var accounts=AccountRepository(db.archive(),PreferencesStore(context))
            accounts.register("Breno","breno@username.test","Password2026","Password2026")
            accounts.register("Other","other@username.test","Password2026","Password2026")
            accounts.updateDetails("breno@username.test","Breno","breno@username.test","","","","@BrenoLeal1234")
            assertTrue(runCatching { accounts.updateDetails("other@username.test","Changed","other@username.test","","","","brenoleal1234") }.isFailure)
            assertEquals("Other",accounts.profile("other@username.test")!!.name)
            db.close();db=Room.databaseBuilder(context,MarvelDatabase::class.java,name).build()
            accounts=AccountRepository(db.archive(),PreferencesStore(context))
            assertEquals("brenoleal1234",accounts.profile("breno@username.test")!!.username)
        } finally { db.close();context.deleteDatabase(name) }
    }
    @Test fun versionTwoMigrationPreservesChatsAndAssignsDistinctHandles() = runBlocking<Unit> {
        val context=context();val name="migration-v2-${System.nanoTime()}.db"
        val original=context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null)
        original.execSQL("CREATE TABLE accounts (email TEXT NOT NULL PRIMARY KEY,name TEXT NOT NULL,passwordHash TEXT NOT NULL,salt TEXT NOT NULL,avatar TEXT NOT NULL)")
        original.execSQL("CREATE TABLE records (owner TEXT NOT NULL,recordKey TEXT NOT NULL,payload TEXT NOT NULL,favorite INTEGER NOT NULL,viewedAt INTEGER NOT NULL,PRIMARY KEY(owner,recordKey))")
        original.execSQL("CREATE TABLE conversations (id TEXT NOT NULL PRIMARY KEY,owner TEXT NOT NULL,title TEXT NOT NULL,payload TEXT NOT NULL,updatedAt INTEGER NOT NULL)")
        original.execSQL("CREATE INDEX index_conversations_owner_updatedAt ON conversations(owner,updatedAt)")
        original.execSQL("INSERT INTO accounts VALUES ('a@test.local','Breno','hash','salt','')")
        original.execSQL("INSERT INTO accounts VALUES ('b@test.local','Breno','hash','salt','')")
        original.execSQL("INSERT INTO conversations VALUES ('chat-v2','a@test.local','Saved chat','{}',123)")
        original.version=2;original.close()
        val db=Room.databaseBuilder(context,MarvelDatabase::class.java,name).addMigrations(MarvelDatabase.MIGRATION_2_3,MarvelDatabase.MIGRATION_3_4).build()
        try {
            val first=db.archive().account("a@test.local").username
            assertTrue(com.example.marvellobby.data.model.Usernames.valid(first))
            assertNotEquals(first,db.archive().account("b@test.local").username)
            assertEquals("Saved chat",db.archive().conversations("a@test.local").single().title)
            assertEquals(first,db.archive().accountByUsername(first).username)
        } finally { db.close();context.deleteDatabase(name) }
    }
}
