package com.example.marvellobby.presentation

import android.view.Gravity
import android.widget.LinearLayout
import com.example.marvellobby.BuildConfig
import com.example.marvellobby.presentation.social.bioField
import com.example.marvellobby.presentation.social.socialStats
import com.example.marvellobby.presentation.social.favoriteSharing

fun ScreenRenderer.profile() {
    val user=state.user ?: return
    profileAvatar()
    add(ui.title(user.name).apply { gravity=Gravity.CENTER },gap=8)
    if(user.email!="guest")add(ui.text("@${user.username}",14,ui.palette.secondary).apply {
        gravity=Gravity.CENTER;tag="profile:username"
    },gap=8) else body("Continue as guest")
    if(user.bio.isNotBlank())add(ui.text(user.bio,14,ui.palette.muted).apply { text=user.bio })
    if(user.email!="guest")menu(if(user.bio.isBlank())"Add bio" else "Edit bio","Tell people a little about yourself") { vm.navigate(Route("editBio")) }
    if(user.online) {
        favoriteSharing()
        user.id?.let { socialStats(it) }
        menu("Community","People · Followers · Messages") { vm.navigate(Route("community")) }
        menu("Messages") { vm.navigate(Route("inbox")) }
        label("Member since")
        add(ui.text(java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(user.joinedAt)),14),gap=8)
        menu("Public profile","See what other users can see") { vm.navigate(Route("publicProfile",userId=user.id)) }
        if(!activity.community.state.value.requiresSignIn)state.onlineProfileNotice?.let { body(it);button("Try again",false) { vm.refreshOnlineProfile() } }
    } else if(user.email!="guest" && vm.onlineAvailable) {
        menu("Connect online account","Keep your saved records on this device") { vm.navigate(Route("connectAccount")) }
    }
    state.formError?.let { body(it) }
    if(state.authBusy)add(ui.loading())
    label("On this device")
    val stats=ui.row()
    val counts=listOf(state.library.count { it.favorite } to "Favorites",state.library.count { it.viewedAt>0 } to "Recently Viewed")
    counts.forEach { (count,name) ->
        val box=ui.column(16).apply { background=ui.gradient(ui.palette.surface,ui.palette.raised) }
        ui.add(box,ui.text(count.toString(),32,ui.palette.secondary,true),0)
        ui.add(box,ui.text(name,12,ui.palette.muted),8)
        stats.addView(box,LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=ui.dp(8) })
    }
    add(stats)
    menu("Favorites") { vm.tab("favorites") }
    menu("Recently Viewed") { vm.navigate(Route("history")) }
    menu("Conversation history") { vm.navigate(Route("chats")) }
    menu("Settings","Account · Appearance · Language") { vm.navigate(Route("settings")) }
    if(user.email=="guest")button("Create account") { vm.navigate(Route("register")) }
}

fun ScreenRenderer.editProfile() {
    val user=state.user ?: return
    profileAvatar()
    formField("Name","profile:name",initial=user.name)
    formField("Username","profile:username",initial=user.username).apply {
        inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        filters=arrayOf(android.text.InputFilter.LengthFilter(25))
    }
    bioField("profile:bio",user.bio)
    label("Account security")
    body("Your stored password cannot be displayed. Enter your current password to change your email or set a new password.")
    if(!state.securityUnlocked)button("Unlock account details",false) { activity.authorizeSensitive { vm.unlockSecurity() } }
    secureField("Email","profile:email",initial=user.email,email=true)
    secureField("Current password","profile:currentPassword")
    secureField("New password (optional)","profile:newPassword")
    secureField("Confirm new password","profile:confirmPassword")
    state.formError?.let { body(it) }
    if(state.authBusy)add(ui.loading())
    add(ui.button("Save changes") {
        activity.hideKeyboard()
        vm.saveAccountDetails(vm.drafts["profile:name"] ?: user.name,vm.drafts["profile:email"] ?: user.email,
            vm.drafts["profile:currentPassword"].orEmpty(),vm.drafts["profile:newPassword"].orEmpty(),vm.drafts["profile:confirmPassword"].orEmpty(),vm.drafts["profile:username"] ?: user.username,vm.drafts["profile:bio"] ?: user.bio)
    }.apply { isEnabled=!state.authBusy },height=52)
}

fun ScreenRenderer.settings() {
    label("ACCOUNT")
    if(state.user?.email!="guest")menu("Edit Profile","Name · Email · Password") { vm.navigate(Route("editProfile")) }
    else menu("Create account") { vm.navigate(Route("register")) }
    label("PREFERENCES")
    if(state.user?.online==true) {
        menu("Activity privacy","Choose what your followers can see") { vm.navigate(Route("socialPrivacy")) }
        menu("Cloud synchronization","Private history and AI conversations") { vm.navigate(Route("cloudSync")) }
    }
    menu("Preferences","Appearance · Language") { vm.navigate(Route("preferences")) }
    label("APP")
    menu("About") { vm.navigate(Route("about")) }
    menu("Privacy & terms") { vm.navigate(Route("privacy")) }
    add(ui.button("Log Out") {
        androidx.appcompat.app.AlertDialog.Builder(activity).setTitle(ui.translate("Log Out"))
            .setMessage(ui.translate("Your saved records and conversations will remain on this device."))
            .setNegativeButton(ui.translate("Cancel"),null)
            .setPositiveButton(ui.translate("Log Out")) { _,_->vm.logout() }.show()
    },gap=36,height=52)
}

fun ScreenRenderer.preferences() {
    title("Preferences")
    label("Appearance")
    listOf("dark" to "Dark","light" to "Light","system" to "System").forEach { (key,label) ->
        button((if(state.preferences.appearance==key)"✓  " else "")+ui.translate(label),false) { vm.appearance(key) }
    }
    label("Language")
    button((if(state.preferences.language=="en")"✓  " else "")+"English",false) { vm.language("en") }
    button((if(state.preferences.language=="pt")"✓  " else "")+"Português",false) { vm.language("pt") }
    body("Catalog descriptions can be translated into Portuguese with Groq. Original text remains available.")
}

fun ScreenRenderer.about() {
    add(ui.brandLogo(140),0,140)
    label("THE CONNECTED UNIVERSE");title("Marvel Lobby")
    body("Version ${BuildConfig.VERSION_NAME}")
    body("Explore characters, teams, powers and stories through connected records.")
    menu("Comic Vine API","Catalog, descriptions and images") { activity.openLink("https://comicvine.gamespot.com/") }
    menu("Gemini API","Contextual Marvel AI conversations") { activity.openLink("https://ai.google.dev/") }
    menu("Groq API","Comic Vine catalog translations") { activity.openLink("https://groq.com/") }
    body("Typography: Plus Jakarta Sans · SIL Open Font License.")
    body("Independent educational project. Marvel characters and related material belong to their respective rights holders. This app is not affiliated with Marvel.")
    if(!vm.onlineAvailable)body("Accounts, favorites and history are saved locally on this device.")
    if(vm.onlineAvailable)body("Only favorites you choose to share appear on your public profile. Activities show new favorites to followers only when enabled. History and AI conversations can optionally sync privately to your account.")
    menu("Privacy & terms") { vm.navigate(Route("privacy")) }
}

fun ScreenRenderer.privacy() {
    title("Privacy & terms")
    label("LOCAL STORAGE")
    body("Your local account, password hash, profile, favorites and history are stored in this app’s private storage. Passwords are never sent to Comic Vine, Gemini or Groq. Clearing app data removes local accounts and saved records.")
    if(vm.onlineAvailable) {
        label("ONLINE ACCOUNT")
        body("When you choose an online account, your name, username, biography, approved avatar and email are sent to the Marvel Lobby server over HTTPS. The server stores a password hash. Your public profile includes your name, username, biography, avatar and join date, without your email or password. Connecting a local account keeps its saved library on this device.")
    }
    body("Only favorites you choose to share appear on your public profile. Activities show new favorites to followers only when enabled. History and AI conversations can optionally sync privately to your account.")
    label("Community")
    body("Following connections, shared records and direct messages are stored on the Marvel Lobby server. Only participants can access a direct conversation through the app. Notifications are shown inside the app. AI conversations are separate and private.")
    label("COMIC VINE")
    body("Search terms and record requests are sent to Comic Vine to load the catalog. Images are downloaded from the image addresses supplied by Comic Vine.")
    label("MARVEL AI")
    body("When you send a message, your question, recent conversation and selected Comic Vine record are sent to Google Gemini. Your account email and password are not included. Chat conversations are saved locally on this device and can be deleted from conversation history.")
    menu("Google privacy policy") { activity.openLink("https://policies.google.com/privacy") }
    label("GROQ / TRANSLATIONS")
    body("Descriptions requested in Portuguese are sent to Groq for translation. Only the catalog excerpt is included, without your account details or chat conversations. Translations are cached locally; original text remains available.")
    menu("Groq privacy policy") { activity.openLink("https://groq.com/privacy-policy/") }
    label("CONTENT")
    body("AI answers may contain errors. Check the linked Comic Vine source records. Catalog availability depends on the external services. This educational app does not claim ownership of Marvel content.")
    menu("Clear history") { vm.clearHistory() }
}
