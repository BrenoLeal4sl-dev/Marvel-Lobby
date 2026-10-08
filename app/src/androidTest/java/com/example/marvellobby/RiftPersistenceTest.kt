package com.example.marvellobby

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.marvellobby.data.local.MarvelDatabase
import com.example.marvellobby.rift.data.StoredRiftRun
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RiftPersistenceTest {
    @Test fun archiveSurvivesRestartAndSeparatesOwnersWithoutTruncatingTotals() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="rift-storage-${UUID.randomUUID()}.db"
        var db=Room.databaseBuilder(context,MarvelDatabase::class.java,name).build()
        try {
            repeat(105) { i ->
                db.rift().insert(StoredRiftRun().apply {
                    id="run-$i";owner="alice";payload="{\"character\":\"${if(i%2==0)"spider-man" else "hulk"}\",\"kills\":2}";status="pending"
                    endedAt=i.toLong();score=i;duration=10f;kills=2;bosses=1
                })
            }
            db.rift().insert(StoredRiftRun().apply { id="bob-run";owner="bob";payload="{}";score=999 })
            assertEquals(-1L,db.rift().insert(StoredRiftRun().apply { id="run-0";owner="alice";payload="{}" }))
            db.rift().status("bob","run-0","synced")
            db.close();db=Room.databaseBuilder(context,MarvelDatabase::class.java,name).build()
            assertEquals(100,db.rift().history("alice").size)
            assertEquals("run-104",db.rift().history("alice").first().id)
            val totals=db.rift().totals("alice")
            assertEquals(105,totals.runs);assertEquals(104,totals.best)
            assertEquals(210,totals.kills);assertEquals(105,totals.bosses)
            assertEquals(105,db.rift().masteryRows("alice").size)
            assertEquals(53,db.rift().masteryRows("alice").count{org.json.JSONObject(it.payload).getString("character")=="spider-man"})
            assertEquals(1,db.rift().masteryRows("bob").size)
            assertEquals(20,db.rift().pending("alice").size)
            assertEquals("run-0",db.rift().pending("alice").first().id)
            db.rift().status("alice","run-0","synced")
            db.rift().status("alice","run-0","rejected")
            assertEquals("synced",db.rift().history("alice").let {
                db.openHelper.readableDatabase.query("SELECT status FROM rift_runs WHERE id='run-0'").use { c->c.moveToFirst();c.getString(0) }
            })
            assertTrue(db.rift().history("unknown").isEmpty())
            assertEquals(999,db.rift().totals("bob").best)
        } finally { db.close();context.deleteDatabase(name) }
    }
}
