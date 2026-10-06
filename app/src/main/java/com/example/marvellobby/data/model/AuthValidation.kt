package com.example.marvellobby.data.model

/** Same password policy as the API; login never applies new-password composition rules. */
object PasswordRules {
    const val MESSAGE="Use 8–128 characters, including a letter and a number."
    fun length(value:String)=value.length in 8..128
    fun letter(value:String)=Regex("\\p{L}").containsMatchIn(value)
    fun number(value:String)=Regex("\\p{N}").containsMatchIn(value)
    fun valid(value:String)=length(value) && letter(value) && number(value)
}

object AuthValidation {
    const val USERNAME_HINT="Use 3–24 letters (a–z), numbers or underscores (_). No dots, spaces or accents."
    const val EMAIL_MESSAGE="Enter a valid email address."
    const val NAME_MESSAGE="Enter a name with 2–80 characters."
    fun validate(register:Boolean,online:Boolean,name:String,email:String,password:String,confirmation:String,username:String):Map<String,String> {
        val errors=linkedMapOf<String,String>()
        if(register) {
            if(name.isBlank())errors["name"]="Enter your name."
            else if(name.trim().length !in 2..80)errors["name"]=NAME_MESSAGE
            if(online) {
                if(username.isBlank())errors["username"]="Choose a username."
                else if(!Usernames.valid(Usernames.normalize(username)))errors["username"]=USERNAME_HINT
            }
        }
        val address=email.trim()
        if(address.isEmpty())errors["email"]="Enter your email."
        else if(address.length>254 || !address.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))errors["email"]=EMAIL_MESSAGE
        if(password.isEmpty())errors["password"]="Enter your password."
        else if(register && !PasswordRules.valid(password))errors["password"]=PasswordRules.MESSAGE
        else if(password.length>128)errors["password"]="Use no more than 128 characters for your password."
        if(register) {
            if(confirmation.isEmpty())errors["confirm"]="Confirm your password."
            else if(password!=confirmation)errors["confirm"]="Passwords do not match."
        }
        return errors
    }
    fun fieldForMessage(message:String):String?=when(message) {
        NAME_MESSAGE -> "name"
        USERNAME_HINT,"Use 3–24 letters, numbers or underscores for your username.","That username is already in use.","That username is already in use on this device." -> "username"
        EMAIL_MESSAGE,"An account with this email already exists.","An account with this email already exists on this device." -> "email"
        PasswordRules.MESSAGE -> "password"
        "Passwords do not match." -> "confirm"
        else -> null
    }
}
