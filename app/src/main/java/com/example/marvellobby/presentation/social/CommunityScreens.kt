package com.example.marvellobby.presentation.social

import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.core.widget.doAfterTextChanged
import com.example.marvellobby.presentation.*
import com.example.marvellobby.data.repository.UserProfile
import com.example.marvellobby.data.model.*
import java.text.DateFormat
import java.util.Date

private val ScreenRenderer.social get()=activity.community

fun ScreenRenderer.communitySessionRequired(): Boolean {
    if(!social.state.value.requiresSignIn)return false
    title("Sign in again")
    body("Your online session has ended. Sign in to reconnect with the community. Your saved records and conversations are kept.")
    add(ui.button("Sign in again") { vm.reauthenticate() }.apply { tag="community:signin" },height=52)
    return true
}

private fun ScreenRenderer.onlineCommunity(): Boolean {
    if(state.user?.online==true)return !communitySessionRequired()
    title("Your universe has company")
    body("Connect an online account to follow people and exchange messages.")
    if(state.user?.email!="guest")button("Connect online account") { vm.navigate(Route("connectAccount")) }
    else button("Create an online account") { vm.navigate(Route("register")) }
    return false
}

fun ScreenRenderer.community() {
    if(!onlineCommunity())return
    label("MARVEL LOBBY / COMMUNITY")
    title("Your universe has company")
    body("Find your people. Share your next discovery.")
    val unread=social.state.value.inbox.unreadTotal
    menu("Messages",if(unread>0)"${ui.translate("Unread messages")}: $unread" else "Your private conversations") { vm.navigate(Route("inbox")) }
    menu("Following activity","Discover what your people are saving") { vm.navigate(Route("activity")) }
    val notifications=social.state.value.notifications.unread
    menu("Notifications",if(notifications>0)"${ui.translate("Unread notifications")}: $notifications" else "Followers and messages") { vm.navigate(Route("notifications")) }
    peopleList()
}

fun ScreenRenderer.socialPeople() {
    if(!onlineCommunity())return
    title(if(state.route.title=="following")"Following" else "Followers")
    peopleList()
}

private fun ScreenRenderer.peopleList() {
    val page=social.state.value.people[state.route.key] ?: PeopleState()
    val field=ui.field("Search people by name or username",page.query,idKey="people:${state.route.key}")
    field.doAfterTextChanged { social.searchPeople(it.toString()) }
    field.imeOptions=EditorInfo.IME_ACTION_SEARCH
    field.setOnEditorActionListener { _,_,_->activity.hideKeyboard();true }
    add(field,height=56)
    add(ui.button("Refresh",false) { social.refreshPeople() }.apply {
        tag="community:refresh";isEnabled=!page.loading;alpha=if(page.loading)0.5f else 1f
    },height=52)
    if(page.loading)add(ui.loading())
    page.error?.let { sectionError(it) { social.loadPeople() } }
    if(page.loaded && !page.loading && page.items.isEmpty()) {
        title("No people here yet")
        body(if(page.query.isBlank())"Profiles will appear here as the community grows." else "Try another name or username.")
    }
    page.items.forEach { user -> add(personRow(user) { vm.navigate(Route("publicProfile",userId=user.id)) },gap=12) }
    if(page.next!=null && !page.loading)button("Load more",false) { social.loadPeople(more=true) }
}

internal fun ScreenRenderer.personRow(user: UserProfile,subtitle: String=user.bio,action: ()->Unit): View {
    val row=ui.row().apply { setPadding(ui.dp(14),ui.dp(16),ui.dp(14),ui.dp(16)) }
    row.addView(ui.avatar(user.avatar,user.name,56),LinearLayout.LayoutParams(ui.dp(56),ui.dp(56)))
    val words=ui.column()
    ui.add(words,ui.text(user.name,16,bold=true).apply { text=user.name },0)
    ui.add(words,ui.text("@${user.username}",12,ui.palette.secondary),5)
    if(subtitle.isNotBlank())ui.add(words,ui.text(subtitle,13,ui.palette.muted).apply {
        text=subtitle;maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END
    },8)
    row.addView(words,LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=ui.dp(14);marginEnd=ui.dp(8) })
    row.addView(ui.icon("chevron","Public profile"))
    ui.clickable(row,onClick=action)
    return row
}

fun ScreenRenderer.socialStats(id: String) {
    if(communitySessionRequired())return
    val data=social.state.value
    val profile=data.profiles[id]
    if(profile!=null) {
        val row=ui.row()
        listOf("Followers" to "followers","Following" to "following").forEach { (label,direction) ->
            val count=if(direction=="followers")profile.followers else profile.following
            val pill=ui.column(16)
            ui.add(pill,ui.text(count.toString(),24,ui.palette.secondary,true),0)
            ui.add(pill,ui.text(label,13),6)
            ui.clickable(pill,onClick={ vm.navigate(Route("socialPeople",title=direction,userId=id)) })
            row.addView(pill,LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=ui.dp(8) })
        }
        add(row)
        if(profile.followsYou && !profile.isSelf)label("Follows you")
        if(!profile.isSelf) {
            add(ui.button(if(profile.isFollowing)"Following · Unfollow" else "Follow",!profile.isFollowing) { social.follow(id) }
                .apply { isEnabled=id !in data.followBusy },height=52)
            add(ui.button("Message",false) { social.openConversation(id) }.apply { isEnabled=!data.opening },height=52)
        }
    } else if(id in data.profileLoading)add(ui.loading())
    data.profileErrors[id]?.let { body(it);button("Try again",false) { social.loadProfile(id) } }
    data.notice?.let(::body)
}

fun ScreenRenderer.inbox() {
    if(!onlineCommunity())return
    title("Messages")
    body("Your private conversations")
    val inbox=social.state.value.inbox
    menu("Find people") { vm.navigate(Route("community")) }
    button("Refresh",false) { social.loadInbox() }
    if(inbox.loading)add(ui.loading())
    inbox.error?.let { sectionError(it) { social.loadInbox() } }
    if(inbox.loaded && inbox.items.isEmpty()) {
        title("Start a conversation")
        body("Open someone's profile and tap Message.")
    }
    inbox.items.forEach { thread ->
        val last=thread.lastMessage
        val preview=last?.shared?.let { ui.translate("Shared record")+" · "+it.name } ?: last?.text ?: ui.translate("Start a conversation")
        val status=if(thread.unread>0)"${ui.translate("Unread messages")}: ${thread.unread}" else last?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(it.sentAt)) }.orEmpty()
        add(personRow(thread.peer,"$status\n$preview") { social.openThread(thread) },gap=12)
    }
    if(inbox.next!=null && !inbox.loading)button("Load more",false) { social.loadInbox(more=true) }
}

fun ScreenRenderer.directChat() {
    if(!onlineCommunity())return
    val id=state.route.userId ?: return
    val data=social.state.value
    val chat=data.chats[id] ?: DirectChatState()
    chat.peer?.let { peer -> add(personRow(peer,"") { vm.navigate(Route("publicProfile",userId=peer.id)) },gap=0) }
    label(if(data.connected)"Live conversation" else "Reconnecting…")
    if(chat.hasOlder && !chat.loading)button("Older messages",false) { social.loadChat(id,older=true) }
    if(chat.loading)add(ui.loading())
    chat.error?.let { body(it);button("Try again",false) { if(chat.loaded)social.syncChat(id) else social.loadChat(id) } }
    if(chat.loaded && chat.messages.isEmpty())body("Say hello. Every connection starts somewhere.")
    chat.messages.forEach { message ->
        val mine=message.senderId==social.userId
        val bubble=ui.column(16).apply {
            background=if(mine)ui.gradient(ui.palette.red,android.graphics.Color.parseColor("#A91529"),22) else ui.shape(ui.palette.surface,border=true)
        }
        if(message.rift!=null)ui.add(bubble,riftCard(message.rift),0)
        else if(message.shared!=null)ui.add(bubble,sharedCard(message.shared),0)
        else {
            val text=ui.text(message.text,14,if(mine)android.graphics.Color.WHITE else ui.palette.text)
            text.text=message.text
            text.setTextIsSelectable(true);ui.add(bubble,text,0)
        }
        val time=DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.sentAt))
        val status=if(mine)" · ${ui.translate(if(message.id<=chat.peerLastRead)"Read" else "Sent")}" else ""
        ui.add(bubble,ui.text(time+status,10,if(mine)0xFFFFD8D5.toInt() else ui.palette.muted),8)
        val lane=ui.row().apply { gravity=if(mine)Gravity.END else Gravity.START }
        lane.addView(bubble,LinearLayout.LayoutParams(-1,-2).apply { if(mine)marginStart=ui.dp(36) else marginEnd=ui.dp(36) })
        add(lane,gap=12)
    }
    if(chat.sending)add(ui.loading())
    chat.pending?.shared?.let { add(sharedCard(it));body("Confirming shared record…") }
    chat.pending?.rift?.let { add(riftCard(it));body("Confirming shared record…") }
    chat.sendError?.let {
        body(it)
        button("Retry sending",false) { social.send(id,retry=true) }
    }
}

internal fun ScreenRenderer.sharedCard(record: SharedContent): View {
    val box=ui.column(14).apply { background=ui.shape(ui.palette.raised,border=true) }
    if(record.imageUrl!=null)ui.add(box,ui.image(record.imageUrl,140,record.name),0,140)
    ui.add(box,ui.label(typeLabel(record.type)),12)
    ui.add(box,ui.text(record.name,18,bold=true).apply { text=record.name },6)
    ui.add(box,ui.text("Open record",12,ui.palette.secondary),10)
    ui.clickable(box,onClick={ vm.open(record.type,ComicReference(record.id,record.name)) })
    return box
}
internal fun ScreenRenderer.riftCard(record: RiftShare): View {
    val box=ui.column(18).apply { background=ui.gradient(0xFF641529.toInt(),0xFF18233C.toInt()) }
    ui.add(box,ui.text("RIFT ARENA",12,0xFFFFA8A1.toInt(),true),0)
    ui.add(box,ui.text(if(record.kind=="challenge")"Rift Challenge" else record.score?.toString().orEmpty(),28,android.graphics.Color.WHITE,true),12)
    ui.add(box,ui.text(if(record.kind=="challenge")"Open challenge" else "Beat this record",13,0xFFF0D8E0.toInt()),12)
    ui.clickable(box,0xFF311526.toInt()) { activity.openRift(challengeId=record.id.takeIf { record.kind=="challenge" }) }
    box.background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33FFFFFF),ui.gradient(0xFF641529.toInt(),0xFF18233C.toInt()),null)
    return box
}
fun ScreenRenderer.shareRift() {
    if(!onlineCommunity())return
    val record=social.state.value.shareRift ?: return
    title("Share Rift Arena");add(riftCard(record));body("Choose someone to send this record to.")
    val page=social.state.value.people[state.route.key] ?: PeopleState()
    val field=ui.field("Search people by name or username",page.query,idKey="rift:people")
    field.doAfterTextChanged { social.searchPeople(it.toString()) };add(field,height=56)
    if(page.loading||social.state.value.opening)add(ui.loading())
    page.error?.let { sectionError(it) { social.loadPeople() } }
    social.state.value.notice?.let(::body)
    page.items.filter { it.id!=social.userId }.forEach { person ->add(personRow(person,ui.translate("Send")) { social.openConversation(person.id!!,rift=record) },gap=12) }
    if(page.next!=null && !page.loading)button("Load more",false) { social.loadPeople(more=true) }
}

fun ScreenRenderer.shareContent() {
    if(!onlineCommunity())return
    val record=social.state.value.shareContent
    if(record==null) { body("Choose a record from its details to share.");return }
    title("Share a record");add(sharedCard(record))
    body("Choose someone to send this record to.")
    val page=social.state.value.people[state.route.key] ?: PeopleState()
    val field=ui.field("Search people by name or username",page.query,idKey="share:people")
    field.doAfterTextChanged { social.searchPeople(it.toString()) };add(field,height=56)
    if(page.loading || social.state.value.opening)add(ui.loading())
    page.error?.let { sectionError(it) { social.loadPeople() } }
    social.state.value.notice?.let(::body)
    if(page.loaded && page.items.none { it.id!=social.userId })body("No people here yet")
    page.items.filter { it.id!=social.userId }.forEach { person ->
        add(personRow(person,ui.translate("Send shared record")) { social.openConversation(person.id!!,record) }.apply { isEnabled=!social.state.value.opening },gap=12)
    }
    if(page.next!=null && !page.loading)button("Load more",false) { social.loadPeople(more=true) }
}

fun ScreenRenderer.followingActivity() {
    if(!onlineCommunity())return
    title("Following activity");body("New favorites shared by people you follow.")
    menu("Activity privacy") { vm.navigate(Route("socialPrivacy")) }
    button("Refresh",false) { social.loadActivity() }
    val page=social.state.value.activity
    if(page.loading)add(ui.loading())
    page.error?.let { sectionError(it) { social.loadActivity() } }
    if(page.loaded && page.items.isEmpty())body("No activity yet. Follow people who choose to share their discoveries.")
    page.items.forEach { item ->
        val caption=when(item.rift?.achievement) {"record"->"New Rift record";"first_boss"->"First Rift boss defeated";else->"Saved a favorite"}
        add(personRow(item.actor,ui.translate(caption)) { vm.navigate(Route("publicProfile",userId=item.actor.id)) })
        add(item.rift?.let(::riftCard) ?: sharedCard(item.content),gap=8)
        body(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(item.at)))
    }
    if(page.next!=null && !page.loading)button("Load more",false) { social.loadActivity(more=true) }
}

fun ScreenRenderer.notifications() {
    if(!onlineCommunity())return
    title("Notifications");body("Followers and messages")
    val page=social.state.value.notifications
    button("Refresh",false) { social.loadNotifications() }
    if(page.items.any { !it.read })button("Mark this page as read",false) { social.readNotifications(page.items) }
    if(page.loading)add(ui.loading())
    page.error?.let { sectionError(it) { social.loadNotifications() } }
    if(page.loaded && page.items.isEmpty())body("You're all caught up. New notifications will appear here.")
    page.items.forEach { item ->
        val caption=ui.translate(if(item.kind=="follow")"Started following you" else "Sent you a message")
        val date=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(item.at))
        add(personRow(item.actor,"${if(!item.read)"● " else ""}$caption\n$date") {
            social.readNotifications(listOf(item))
            if(item.conversationId!=null)social.openThread(DirectConversation(item.conversationId,item.actor))
            else vm.navigate(Route("publicProfile",userId=item.actor.id))
        },gap=12)
    }
    if(page.next!=null && !page.loading)button("Load more",false) { social.loadNotifications(more=true) }
}

fun ScreenRenderer.socialPrivacy() {
    if(!onlineCommunity())return
    title("Activity privacy")
    body("Only new favorites are shared with your followers. Your viewing history and AI conversations are never shown in this feed.")
    body("Turning sharing off removes your existing activities. Turning it on again starts with future favorites.")
    val choice=social.state.value.activitySharing
    if(choice.busy)add(ui.loading())
    choice.error?.let { sectionError(it) { social.activityPrivacy() } }
    if(choice.enabled!=null)button(if(choice.enabled)"Stop sharing activity" else "Share new favorites with followers",false) { social.activityPrivacy(!choice.enabled) }
}

fun ScreenRenderer.directComposer(): View {
    val id=state.route.userId.orEmpty()
    val chat=social.state.value.chats[id] ?: DirectChatState()
    val row=ui.row().apply { setPadding(ui.dp(16),ui.dp(10),ui.dp(16),ui.dp(12));setBackgroundColor(ui.palette.background) }
    val field=ui.field("Write a message",social.drafts[id].orEmpty(),idKey="direct:$id").apply {
        setSingleLine(false);maxLines=4;filters=arrayOf(InputFilter.LengthFilter(2000))
        inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        doAfterTextChanged { social.drafts[id]=it.toString() }
    }
    row.addView(field,LinearLayout.LayoutParams(0,-2,1f))
    row.addView(ui.actionIcon(com.example.marvellobby.R.drawable.ic_send,"Send",ui.palette.red) { social.send(id) }.apply {
        tag="direct:send";isEnabled=!social.state.value.requiresSignIn && !chat.sending && chat.pending==null && chat.loaded;alpha=if(isEnabled)1f else 0.4f
    },LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)).apply { marginStart=ui.dp(8) })
    return row
}
