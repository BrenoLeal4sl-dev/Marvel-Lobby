package com.example.marvellobby

import com.example.marvellobby.data.model.*
import com.example.marvellobby.data.repository.UserProfile
import com.example.marvellobby.presentation.social.*
import org.junit.Assert.*
import org.junit.Test

class DirectChatStateTest {
    private val peer=UserProfile("Peer","",username="peer",id="peer")
    private fun message(id: Long,sender: String="me")=DirectMessage(id,"conversation",sender,"nonce-$id","Message $id",id)
    private fun page(vararg ids: Long)=DirectPage(peer,ids.map { message(it,if(it==2L)"peer" else "me") },false,0)

    @Test fun anOwnAckDoesNotSkipAPrecedingIncomingMessageOnTheNextLiveFetch() {
        val initial=DirectChatState(loaded=true).receivePage(page(1))
        val acknowledged=initial.acknowledge(message(3))
        assertEquals(listOf(1L,3L),acknowledged.messages.map { it.id })
        assertEquals("Fetch must still request messages after 1, not after the ACK 3",1L,acknowledged.syncedThrough)
        val received=acknowledged.receivePage(page(2,3))
        assertEquals(listOf(1L,2L,3L),received.messages.map { it.id })
        assertEquals(3L,received.syncedThrough)
        assertEquals("peer",received.messages[1].senderId)
    }
    @Test fun olderPagesAndRepeatedSignalsNeverMoveTheCursorOrReadReceiptBackwards() {
        val initial=DirectChatState().receivePage(page(20,30).copy(peerLastRead=30))
        val older=initial.receivePage(page(5,10).copy(peerLastRead=10),advanceCursor=false)
        assertEquals(30L,older.syncedThrough);assertEquals(30L,older.peerLastRead)
        val repeated=older.receivePage(page(20,30))
        assertEquals(listOf(5L,10L,20L,30L),repeated.messages.map { it.id })
        assertEquals(30L,repeated.syncedThrough);assertEquals(30L,repeated.peerLastRead)
    }
    @Test fun emptyHistoryAndFirstSendStillFetchFromZeroAfterReconnect() {
        val initial=DirectChatState(loaded=true).receivePage(page())
        val sent=initial.acknowledge(message(8))
        assertEquals(0L,sent.syncedThrough)
        val recovered=sent.receivePage(page(7,8))
        assertEquals(listOf(7L,8L),recovered.messages.map { it.id })
        assertEquals(8L,recovered.syncedThrough)
        assertEquals(recovered,recovered.receivePage(page(7,8)))
    }
}
