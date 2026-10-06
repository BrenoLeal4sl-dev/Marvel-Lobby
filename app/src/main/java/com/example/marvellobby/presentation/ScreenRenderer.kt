package com.example.marvellobby.presentation

import com.example.marvellobby.presentation.auth.*
import com.example.marvellobby.presentation.home.*
import com.example.marvellobby.presentation.explore.*
import com.example.marvellobby.presentation.search.*
import com.example.marvellobby.presentation.catalog.*
import com.example.marvellobby.presentation.social.*
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.widget.doAfterTextChanged
import com.example.marvellobby.MainActivity
import com.example.marvellobby.data.model.*

class ScreenRenderer(val activity: MainActivity,val vm: MainViewModel,val state: AppState,val ui: UiKit) {
    val content=ui.column().apply { setPadding(ui.dp(20),ui.dp(8),ui.dp(20),ui.dp(32)) }
    fun add(view: View,gap: Int=20,height: Int=-2)=ui.add(content,view,gap,height)
    fun carousel(items: List<ComicEntity>,key: String,width: Int=260,height: Int=330) {
        val strip=ui.row()
        items.forEach { entity -> strip.addView(ui.poster(entity,width,height) { vm.open(entity) }) }
        val positionKey="carousel:${state.route.key}:$key"
        val savedX=vm.scrollPositions[positionKey] ?: 0
        add(HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled=false;addView(strip)
            post { scrollTo(savedX,0) }
            setOnScrollChangeListener { _: View,x: Int,_: Int,_: Int,_: Int -> vm.scrollPositions[positionKey]=x }
        })
    }
    fun label(value: String)=add(ui.label(value))
    fun title(value: String)=add(ui.title(value))
    fun body(value: String)=add(ui.text(value,14,ui.palette.muted))
    fun button(value: String,primary: Boolean=true,action: ()->Unit)=add(ui.button(value,primary,action),height=52)
    fun menu(title: String,subtitle: String="",action: ()->Unit)=add(ui.menu(title,subtitle,action))
    fun sectionError(message: String,retry: ()->Unit) {
        title("Something went wrong");body(message);button("Try again",action=retry)
    }
    fun empty(title: String,message: String,action: ()->Unit) {
        add(ui.editorial("ARCHIVE / 00",title,160));body(message);button("Explore the archive",action=action)
    }
    fun card(entity: com.example.marvellobby.data.model.ComicEntity)=add(ui.card(entity) { vm.open(entity) })
    fun formField(label: String,key: String,password: Boolean=false,email: Boolean=false,initial: String=""): EditText {
        val box=ui.column()
        ui.add(box,ui.text(label,12,ui.palette.secondary,true),0)
        val field=ui.field(label,vm.drafts[key] ?: initial,password=password,email=email,idKey=key)
        field.doAfterTextChanged { vm.drafts[key]=it.toString() }
        ui.add(box,field,8,56);add(box)
        return field
    }
    fun render(): LinearLayout {
        when(state.route.screen) {
            "welcome" -> welcome()
            "login","register" -> auth()
            "home" -> home()
            "explore" -> explore()
            "search" -> search()
            "catalog" -> catalog()
            "filters" -> filters()
            "detail" -> detail()
            "favorites" -> favorites()
            "history" -> history()
            "ai" -> ai()
            "chats" -> chatHistory()
            "profile" -> profile()
            "editProfile" -> editProfile()
            "editBio" -> editBio()
            "connectAccount" -> connectAccount()
            "publicProfile" -> publicProfile()
            "community" -> community()
            "socialPeople" -> socialPeople()
            "inbox" -> inbox()
            "directChat" -> directChat()
            "settings" -> settings()
            "preferences" -> preferences()
            "about" -> about()
            "privacy" -> privacy()
        }
        return content
    }
    fun searchField(key: String="search",editable: Boolean=true,gap: Int=0) {
        if(!editable) {
            val box=ui.row().apply { background=ui.shape(ui.palette.surface,border=true);setPadding(ui.dp(16),0,ui.dp(16),0) }
            box.addView(ui.text("Search the Marvel Universe"),LinearLayout.LayoutParams(0,-2,1f))
            box.addView(ui.icon("search","Search"))
            box.setOnClickListener { vm.navigate(Route("search")) };box.isFocusable=true
            add(box,gap,55)
            return
        }
        val field=ui.field("Search the Marvel Universe",vm.drafts["query:$key"].orEmpty(),idKey="query:$key")
        field.imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        field.doAfterTextChanged { vm.search(it.toString()) }
        field.setOnEditorActionListener { _,_,_ -> activity.hideKeyboard();true }
        add(field,gap,55)
    }
    fun typeLabel(type: ResourceType)=when(type) {
        ResourceType.CHARACTER -> "Characters"; ResourceType.TEAM -> "Teams"; ResourceType.POWER -> "Powers";ResourceType.STORY_ARC -> "Story Arcs";ResourceType.ISSUE -> "Issues";else -> type.name
    }
    fun typeTabs(selected: ResourceType?,all: Boolean=false,extraTypes: List<ResourceType> = emptyList(),onClick: (ResourceType?)->Unit) {
        val row=ui.row()
        val choices=mutableListOf<Pair<ResourceType?,String>>()
        if(all)choices.add(null to "All")
        choices.addAll((listOf(ResourceType.CHARACTER,ResourceType.TEAM,ResourceType.POWER,ResourceType.STORY_ARC)+extraTypes).distinct().map { it to typeLabel(it) })
        for((type,name)in choices) {
            val chip=ui.button(name,selected==type) { onClick(type) }
            row.addView(chip,LinearLayout.LayoutParams(-2,ui.dp(48)).apply { marginEnd=ui.dp(8) })
        }
        add(HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled=false;addView(row) })
    }
}

