package com.example.marvellobby.data.repository

import android.util.Patterns
import com.example.marvellobby.data.local.*
import com.example.marvellobby.data.model.Usernames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class UserProfile(val name: String, val email: String, val avatar: String="",val username: String="",
    val id: String?=null,val bio: String="",val joinedAt: Long=0L) {
    val online get()=id!=null
    val ownerKey get()=id?.let { "remote:$it" } ?: email
}
class AccountRepository(private val dao: ArchiveDao, private val preferences: PreferencesStore) {
    suspend fun register(name: String, email: String, password: String, confirm: String): UserProfile = withContext(Dispatchers.IO) {
        require(name.trim().length in 2..80) { "Enter a name with 2–80 characters." }
        val normalized=email.trim().lowercase(Locale.ROOT)
        require(Patterns.EMAIL_ADDRESS.matcher(normalized).matches()) { "Enter a valid email address." }
        require(password.length in 8..128 && password.any(Char::isLetter) && password.any(Char::isDigit)) { "Use 8–128 characters, including a letter and a number." }
        require(password == confirm) { "Passwords do not match." }
        require(dao.account(normalized)==null) { "An account with this email already exists on this device." }
        val account=LocalAccount().apply {
            this.name=name.trim(); this.email=normalized; salt=PasswordHasher.salt(); passwordHash=PasswordHasher.hash(password,salt)
            do { username=Usernames.create(name) } while(dao.accountByUsername(username)!=null)
        }
        dao.insertAccount(account)
        preferences.session(normalized)
        UserProfile(account.name,account.email,account.avatar,account.username,bio=account.bio)
    }
    suspend fun login(email: String, password: String): UserProfile = withContext(Dispatchers.IO) {
        val account=dao.account(email.trim().lowercase(Locale.ROOT))
        require(dao.binding(email.trim().lowercase(Locale.ROOT))==null) { "This account is online. Use online sign-in." }
        require(account != null && PasswordHasher.verify(password,account.salt,account.passwordHash)) { "Email or password is incorrect." }
        preferences.session(account.email)
        UserProfile(account.name,account.email,account.avatar,account.username,bio=account.bio)
    }
    suspend fun profile(email: String): UserProfile? = withContext(Dispatchers.IO) {
        when {
            email=="guest" -> UserProfile("Guest","guest")
            email.startsWith("remote:") -> dao.remoteAccount(email)?.let { row ->
                runCatching { com.google.gson.Gson().fromJson(row.payload,UserProfile::class.java) }.getOrNull()?.takeIf { it.ownerKey==email }
            }
            dao.binding(email)!=null -> null
            else -> dao.account(email)?.let { UserProfile(it.name,it.email,it.avatar,it.username,bio=it.bio) }
        }
    }
    suspend fun verifyLocal(owner: String,password: String)=withContext(Dispatchers.IO) {
        val account=dao.account(owner)
        require(account!=null && PasswordHasher.verify(password,account.salt,account.passwordHash)) { "Current password is incorrect." }
        require(dao.binding(owner)==null) { "This account is online. Use online sign-in." }
    }
    suspend fun update(email: String,name: String,avatar: String): UserProfile = withContext(Dispatchers.IO) {
        require(name.trim().length in 2..80) { "Enter a name with 2–80 characters." }
        val account=dao.account(email) ?: error("Create a local account to edit your profile.")
        account.name=name.trim(); account.avatar=avatar; dao.updateAccount(account)
        UserProfile(account.name,account.email,account.avatar,account.username,bio=account.bio)
    }
    suspend fun bio(owner: String,value: String): UserProfile=withContext(Dispatchers.IO) {
        val text=value.trim();require(text.codePointCount(0,text.length)<=280) { "Keep your bio within 280 characters." }
        val account=dao.account(owner) ?: error("Account unavailable.")
        account.bio=text;dao.updateAccount(account)
        UserProfile(account.name,account.email,account.avatar,account.username,bio=account.bio)
    }
    suspend fun updateDetails(email: String,name: String,nextEmail: String,currentPassword: String,
        newPassword: String,confirm: String,username: String?=null,bio: String?=null): UserProfile = withContext(Dispatchers.IO) {
        require(name.trim().length in 2..80) { "Enter a name with 2–80 characters." }
        val normalized=nextEmail.trim().lowercase(Locale.ROOT)
        require(Patterns.EMAIL_ADDRESS.matcher(normalized).matches()) { "Enter a valid email address." }
        val account=dao.account(email) ?: error("Account unavailable.")
        bio?.trim()?.let { require(it.codePointCount(0,it.length)<=280) { "Keep your bio within 280 characters." };account.bio=it }
        val handle=username?.let(Usernames::normalize) ?: account.username
        require(Usernames.valid(handle)) { "Use 3–24 letters, numbers or underscores for your username." }
        require(dao.accountByUsername(handle)?.email in listOf(null,email)) { "That username is already in use on this device." }
        if(normalized!=email || newPassword.isNotEmpty()) {
            require(PasswordHasher.verify(currentPassword,account.salt,account.passwordHash)) { "Current password is incorrect." }
        }
        if(normalized!=email)require(dao.account(normalized)==null) { "An account with this email already exists on this device." }
        if(newPassword.isNotEmpty()) {
            require(newPassword.length in 8..128 && newPassword.any(Char::isLetter) && newPassword.any(Char::isDigit)) { "Use 8–128 characters, including a letter and a number." }
            require(newPassword==confirm) { "Passwords do not match." }
            account.salt=PasswordHasher.salt();account.passwordHash=PasswordHasher.hash(newPassword,account.salt)
        }
        account.name=name.trim();account.email=normalized;account.username=handle
        if(email==normalized)dao.updateAccount(account) else {
            dao.changeAccountEmail(email,account)
        }
        UserProfile(account.name,account.email,account.avatar,account.username,bio=account.bio)
    }
}
