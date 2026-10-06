package com.example.marvellobby.data.model

import com.example.marvellobby.data.repository.UserProfile

data class SocialProfile(val profile: UserProfile,val followers: Int,val following: Int,
    val isFollowing: Boolean,val followsYou: Boolean,val isSelf: Boolean)
data class PeoplePage(val items: List<UserProfile>,val next: String?)
data class DirectMessage(val id: Long,val conversationId: String,val senderId: String,val clientId: String,
    val text: String,val sentAt: Long)
data class DirectConversation(val id: String,val peer: UserProfile,val unread: Int=0,val lastMessage: DirectMessage?=null)
data class InboxPage(val items: List<DirectConversation>,val next: Int?,val unreadTotal: Int)
data class DirectPage(val peer: UserProfile,val items: List<DirectMessage>,val hasMore: Boolean,val peerLastRead: Long)
data class CommunityEvent(val type: String,val conversationId: String?=null)
data class PublicFavorite(val id: Int,val type: ResourceType,val name: String,val imageUrl: String?)
data class PublicFavoritesPage(val items: List<PublicFavorite>,val next: Int?,val visible: Boolean)

/** ACKs and live deliveries may race. One server identity/client nonce always occupies one bubble. */
fun mergeDirectMessages(existing: List<DirectMessage>,incoming: List<DirectMessage>): List<DirectMessage> =
    (existing+incoming).associateBy { it.senderId to it.clientId }.values.distinctBy { it.id }.sortedBy { it.id }
