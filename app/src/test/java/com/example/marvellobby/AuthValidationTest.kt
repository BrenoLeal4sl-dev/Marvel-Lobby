package com.example.marvellobby

import com.example.marvellobby.data.model.*
import com.example.marvellobby.data.local.AppPreferences
import com.example.marvellobby.presentation.Route
import com.example.marvellobby.presentation.Translations
import com.example.marvellobby.presentation.auth.StartupDestination
import org.junit.Assert.*
import org.junit.Test

class AuthValidationTest {
    private fun signup(username:String="breno_1234",password:String="password1",confirmation:String=password)=
        AuthValidation.validate(true,true,"Breno","breno@example.invalid",password,confirmation,username)

    @Test fun missingFieldsAreIdentifiedIndividuallyInFormOrder() {
        assertEquals(listOf("name","username","email","password","confirm"),AuthValidation.validate(true,true,"","","","","").keys.toList())
        assertEquals(listOf("email","password"),AuthValidation.validate(false,true,"","","","","").keys.toList())
    }
    @Test fun usernameRulesMatchApiIncludingOptionalAtSignAndNormalization() {
        for(value in listOf("breno.leal","breno leal","bréno","ab","a".repeat(25),"___"))
            assertEquals(AuthValidation.USERNAME_HINT,signup(value)["username"])
        assertTrue(signup(" @Breno_1234 ").isEmpty())
        assertTrue(AuthValidation.validate(true,false,"Breno","breno@example.invalid","password1","password1","ignored.invalid").isEmpty())
    }
    @Test fun passwordChecklistMatchesActualPolicyWithoutInventingUppercaseOrSymbolRequirements() {
        assertTrue(signup().isEmpty())
        assertTrue(signup(password="ábcdefg１").isEmpty())
        for(value in listOf("abc1234","abcdefgh","12345678","a1"+"x".repeat(127))) {
            assertEquals(PasswordRules.MESSAGE,signup(password=value)["password"])
            assertFalse(PasswordRules.valid(value))
        }
        assertTrue(PasswordRules.valid("a1"+"x".repeat(126)))
        assertFalse(PasswordRules.letter("12345678"));assertFalse(PasswordRules.number("abcdefgh"))
        assertEquals("Passwords do not match.",signup(confirmation="password2")["confirm"])
    }
    @Test fun loginDoesNotApplyNewPasswordCompositionRulesToExistingCredentials() {
        assertTrue(AuthValidation.validate(false,true,"","breno@example.invalid","old","","").isEmpty())
        assertEquals(setOf("email"),AuthValidation.validate(false,true,"","not-an-email","old","","").keys)
        assertEquals(setOf("password"),AuthValidation.validate(false,true,"","breno@example.invalid","x".repeat(129),"","").keys)
    }
    @Test fun startupRequiresLanguageBeforeOnboardingAndPreservesAuthenticatedDestination() {
        assertEquals("language",StartupDestination.resolve(AppPreferences(),false).screen)
        assertEquals("language",StartupDestination.resolve(AppPreferences(onboarded=true),true,Route("profile")).screen)
        val chosen=AppPreferences(language="pt",languageChosen=true)
        assertEquals("welcome",StartupDestination.resolve(chosen,false).screen)
        assertEquals("login",StartupDestination.resolve(chosen.copy(onboarded=true),false).screen)
        assertEquals("home",StartupDestination.resolve(chosen.copy(onboarded=true),true,Route("language")).screen)
        val detail=Route("detail",type=ResourceType.CHARACTER,id=1440)
        assertEquals(detail,StartupDestination.resolve(chosen.copy(onboarded=true),true,detail))
        assertEquals("home",StartupDestination.resolve(chosen.copy(onboarded=true),true,Route("login")).screen)
    }
    @Test fun validationMessagesAndPasswordHintsAreAvailableInBothLanguages() {
        for(message in listOf(AuthValidation.USERNAME_HINT,AuthValidation.EMAIL_MESSAGE,PasswordRules.MESSAGE,"Enter your name.","Choose a username.","Enter your email.","Enter your password.","Confirm your password.","At least one letter","At least one number","Uppercase letters and symbols are optional.","Show password","Hide password")) {
            assertEquals(message,Translations.text(message,"en"))
            assertNotEquals(message,Translations.text(message,"pt"))
        }
        assertEquals("username",AuthValidation.fieldForMessage("That username is already in use."))
        assertEquals("email",AuthValidation.fieldForMessage("An account with this email already exists."))
        assertNull(AuthValidation.fieldForMessage("Email or password is incorrect."))
    }
}
