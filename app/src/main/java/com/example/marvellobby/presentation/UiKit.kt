package com.example.marvellobby.presentation

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.HtmlCompat
import coil.load
import coil.decode.SvgDecoder
import com.example.marvellobby.R
import com.example.marvellobby.data.model.*

class Palette(light: Boolean,ai: Boolean=false) {
    val background=Color.parseColor(if(ai) "#08070A" else if(light) "#FAF6F4" else "#0C090A")
    val surface=Color.parseColor(if(light && !ai) "#FFFFFF" else "#1B1518")
    val raised=Color.parseColor(if(light && !ai) "#F2E7E4" else "#2B1F24")
    val text=Color.parseColor(if(light && !ai) "#28191A" else "#FFF5F2")
    val muted=Color.parseColor(if(light && !ai) "#735D5C" else "#BEA8AD")
    val secondary=Color.parseColor(if(light && !ai) "#B41F2C" else "#FF918A")
    val red=Color.parseColor(if(light && !ai) "#D81E30" else "#F02A3D")
    val paper=Color.parseColor("#F4EBE4")
    val ink=Color.parseColor("#28191A")
    val border=Color.parseColor(if(light && !ai) "#E2D1CE" else "#493036")
}
class UiKit(val context: Context,val palette: Palette,val translate: (String)->String) {
    private val font=ResourcesCompat.getFont(context,R.font.plus_jakarta_sans)!!
    fun dp(value: Int)=(value*context.resources.displayMetrics.density).toInt()
    fun shape(color: Int,radius: Int=22,border: Boolean=false)=GradientDrawable().apply {
        setColor(color);cornerRadius=dp(radius).toFloat();if(border) setStroke(dp(1),palette.border)
    }
    fun clickable(view: View,color: Int=palette.surface,onClick: ()->Unit) {
        view.background=RippleDrawable(ColorStateList.valueOf(0x33F02A3D),shape(color),null)
        view.isClickable=true;view.isFocusable=true;view.setOnClickListener { onClick() }
    }
    fun column(padding: Int=0)=LinearLayout(context).apply {
        orientation=LinearLayout.VERTICAL;setPadding(dp(padding),dp(padding),dp(padding),dp(padding))
        layoutParams=LinearLayout.LayoutParams(-1,-2)
    }
    fun row()=LinearLayout(context).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;layoutParams=LinearLayout.LayoutParams(-1,-2) }
    fun add(parent: LinearLayout,view: View,gap: Int=20,height: Int=-2,width: Int=-1) {
        val params=LinearLayout.LayoutParams(if(width<0)width else dp(width),if(height<0)height else dp(height))
        if(parent.childCount>0)params.topMargin=dp(gap)
        parent.addView(view,params)
    }
    fun text(value: String,size: Int=14,color: Int=palette.text,bold: Boolean=false)=TextView(context).apply {
        text=translate(value);textSize=size.toFloat();setTextColor(color)
        typeface=Typeface.create(font,if(bold)700 else 400,false)
        includeFontPadding=false;setLineSpacing(dp(3).toFloat(),1f)
        layoutParams=LinearLayout.LayoutParams(-1,-2)
    }
    fun label(value: String)=text(value,12,palette.secondary,true)
    fun title(value: String)=text(value,28,palette.text,true)
    fun brandLogo(height: Int=160)=ImageView(context).apply {
        setImageResource(R.drawable.marvel_lobby_logo)
        contentDescription="Marvel Lobby"
        scaleType=ImageView.ScaleType.FIT_CENTER
        adjustViewBounds=true
        layoutParams=LinearLayout.LayoutParams(-1,dp(height))
    }
    fun button(label: String,primary: Boolean=true,onClick: ()->Unit)=Button(context).apply {
        text=translate(label);isAllCaps=false;textSize=14f;typeface=Typeface.create(font,600,false)
        setTextColor(if(primary)Color.WHITE else palette.text);stateListAnimator=null
        minHeight=dp(52);minimumHeight=dp(52);setPadding(dp(16),dp(12),dp(16),dp(12))
        backgroundTintList=null;clickable(this,if(primary)palette.red else palette.raised,onClick)
        if(primary)background=RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),gradient(palette.red,Color.parseColor("#AB1223"),22),shape(Color.WHITE,22))
        contentDescription=translate(label)
    }
    fun icon(name: String,description: String,onClick: (() -> Unit)?=null): View {
        val icon=ImageView(context).apply {
            load("file:///android_asset/icons/$name.svg") { decoderFactory(SvgDecoder.Factory()) }
            imageTintList=ColorStateList.valueOf(palette.secondary);contentDescription=translate(description)
            scaleType=ImageView.ScaleType.FIT_CENTER
        }
        if(onClick==null) { icon.layoutParams=LinearLayout.LayoutParams(dp(24),dp(24));return icon }
        return FrameLayout(context).apply {
            layoutParams=LinearLayout.LayoutParams(dp(48),dp(48));contentDescription=translate(description)
            addView(icon,FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));clickable(this,palette.surface,onClick)
        }
    }
    fun actionIcon(drawable: Int,description: String,color: Int=palette.surface,onClick: ()->Unit): View = FrameLayout(context).apply {
        layoutParams=LinearLayout.LayoutParams(dp(48),dp(48));contentDescription=translate(description)
        addView(ImageView(context).apply { setImageResource(drawable);imageTintList=ColorStateList.valueOf(if(color==palette.red)Color.WHITE else palette.secondary) },FrameLayout.LayoutParams(dp(23),dp(23),Gravity.CENTER))
        clickable(this,color,onClick)
    }
    fun avatar(uri: String,name: String,size: Int=112): View = FrameLayout(context).apply {
        layoutParams=LinearLayout.LayoutParams(dp(size),dp(size))
        background=shape(palette.raised,1000,true);clipToOutline=true;contentDescription=name
        addView(text(name.take(2).uppercase(),if(size<64)16 else 30,palette.secondary,true).apply { gravity=Gravity.CENTER },FrameLayout.LayoutParams(-1,-1))
        if(uri.isNotBlank())addView(TopCropImageView(context).apply { load(uri) { crossfade(true) };contentDescription=name },FrameLayout.LayoutParams(-1,-1))
    }
    fun menu(title: String,subtitle: String="",onClick: ()->Unit): View {
        val row=row().apply { setPadding(dp(16),dp(16),dp(16),dp(16));minimumHeight=dp(if(subtitle.isBlank())64 else 86) }
        val content=column()
        add(content,text(title,18,bold=true),0)
        if(subtitle.isNotBlank())add(content,text(subtitle,12,palette.muted),4)
        row.addView(content,LinearLayout.LayoutParams(0,-2,1f))
        row.addView(icon("chevron",title))
        clickable(row,palette.surface,onClick)
        return row
    }
    fun field(label: String,value: String,hint: String="",password: Boolean=false,email: Boolean=false,idKey: String): EditText =
        EditText(context).apply {
            id=0x01000000 or (idKey.hashCode() and 0x00FFFFFF)
            setText(value);this.hint=translate(if(hint.isBlank())label else hint);contentDescription=translate(label)
            textSize=14f;setTextColor(palette.text);setHintTextColor(palette.muted)
            typeface=Typeface.create(font,400,false);isSingleLine=true
            inputType=when { password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD;email -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES }
            typeface=Typeface.create(font,400,false)
            background=shape(palette.surface,border=true)
            setPadding(dp(16),dp(12),dp(16),dp(12));minimumHeight=dp(56)
            importantForAutofill=if(password||email)View.IMPORTANT_FOR_AUTOFILL_YES else View.IMPORTANT_FOR_AUTOFILL_NO
            if(password)setAutofillHints(View.AUTOFILL_HINT_PASSWORD)
            if(email)setAutofillHints(View.AUTOFILL_HINT_EMAIL_ADDRESS)
        }
    fun issueRow(reference: ComicReference,record: ComicEntity?,onClick: ()->Unit): View {
        val line=row().apply { setPadding(dp(18),dp(16),dp(18),dp(16));minimumHeight=dp(76) }
        val heading=record?.issueLabel ?: reference.issueNumber?.let { "#$it" }
            ?: reference.name.takeIf { reference.hasTitle } ?: translate("Issue")
        val story=record?.name?.takeIf { it!=record.issueLabel && it!="Issue" }
            ?: reference.name.takeIf { reference.hasTitle && it!=heading }
        val subtitle=story ?: if(record?.issueLabel==null && reference.issueNumber==null)"Comic Vine ID · ${reference.id}" else ""
        val copy=column()
        add(copy,text(clean(heading).replace(Regex("\\s+")," "),16,bold=true).apply {
            maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END
        },0)
        if(subtitle.isNotBlank())add(copy,text(clean(subtitle).replace(Regex("\\s+")," "),12,palette.muted).apply {
            maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END
        },6)
        line.addView(copy,LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=dp(12) })
        line.addView(icon("chevron","Issue"))
        line.contentDescription=clean(heading)+" "+clean(subtitle)
        clickable(line,palette.surface,onClick)
        return line
    }
    fun image(url: String?,height: Int,name: String): View {
        val frame=FrameLayout(context).apply { background=shape(palette.raised);clipToOutline=true;minimumHeight=dp(height) }
        val fallback=text(name.uppercase(),24,palette.secondary,true).apply { gravity=Gravity.CENTER;setPadding(dp(20),0,dp(20),0) }
        frame.addView(fallback,FrameLayout.LayoutParams(-1,-1))
        if(!url.isNullOrBlank())frame.addView(TopCropImageView(context).apply {
            contentDescription=name
            load(url) { crossfade(true); listener(onSuccess={_,_->fallback.visibility=View.GONE}) }
        },FrameLayout.LayoutParams(-1,-1))
        frame.layoutParams=LinearLayout.LayoutParams(-1,dp(height))
        return frame
    }
    fun editorial(label: String,title: String,height: Int=144,onClick: (() -> Unit)?=null): View {
        val box=column(20).apply { background=gradient(palette.surface,palette.raised);minimumHeight=dp(height) }
        add(box,text(label,12,palette.secondary,true),0)
        add(box,text(title,24,palette.text,true),24)
        if(onClick!=null) { box.setOnClickListener { onClick() };box.isFocusable=true }
        return box
    }
    fun power(name: String,index: Int=1,onClick: ()->Unit): View {
        val row=row().apply { background=shape(palette.surface,border=true);setPadding(dp(16),dp(16),dp(16),dp(16));minimumHeight=dp(60) }
        row.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_power_bolt)
            imageTintList=ColorStateList.valueOf(palette.secondary)
            importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        },LinearLayout.LayoutParams(dp(22),dp(22)).apply { marginEnd=dp(14) })
        row.addView(text(name,18,bold=true),LinearLayout.LayoutParams(0,-2,1f))
        row.addView(icon("chevron",name));row.setOnClickListener { onClick() };row.isFocusable=true
        return row
    }
    fun card(entity: ComicEntity,onClick: ()->Unit): View {
        if(entity.type==ResourceType.POWER)return power(entity.name,entity.id) { onClick() }
        val box=column(16).apply { background=shape(palette.surface) }
        if(entity.type==ResourceType.CHARACTER) {
            val row=row()
            row.addView(image(entity.imageUrl,104,entity.name),LinearLayout.LayoutParams(dp(80),dp(104)))
            val copy=column().apply { setPadding(dp(12),0,0,0) }
            add(copy,text(entity.name,18,bold=true),0)
            entity.realName?.let { add(copy,text(it,12,palette.secondary),4) }
            add(copy,text(clean(entity.summary ?: entity.publisher?.name.orEmpty()),14,palette.muted).apply { maxLines=3;ellipsize=android.text.TextUtils.TruncateAt.END },4)
            row.addView(copy,LinearLayout.LayoutParams(0,-2,1f));add(box,row,0)
        } else {
            add(box,image(entity.imageUrl,132,entity.name),0,132)
            add(box,label(entity.type.name.replace('_',' ')),12)
            add(box,text(entity.name,22,bold=true),8)
            entity.issueLabel?.let { add(box,text(it,14,palette.secondary,true),8) }
            entity.summary?.let { add(box,text(clean(it),14,palette.muted).apply { maxLines=3;ellipsize=android.text.TextUtils.TruncateAt.END },12) }
        }
        clickable(box,palette.surface,onClick)
        return box
    }

    fun gradient(start: Int,end: Int,radius: Int=28)=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(start,end)).apply {
        cornerRadius=dp(radius).toFloat()
    }
    fun poster(entity: ComicEntity,width: Int=260,height: Int=330,onClick: ()->Unit): View {
        val frame=FrameLayout(context).apply {
            background=shape(palette.surface,28);clipToOutline=true
            contentDescription=entity.name;isFocusable=true;setOnClickListener { onClick() }
            layoutParams=LinearLayout.LayoutParams(dp(width),dp(height)).apply { marginEnd=dp(14) }
        }
        frame.addView(image(entity.imageUrl,height,entity.name),FrameLayout.LayoutParams(-1,-1))
        frame.addView(View(context).apply {
            background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0x00000000,0x4410090B,0xF210090B.toInt()))
        },FrameLayout.LayoutParams(-1,-1))
        val copy=column(22)
        add(copy,text(entity.publisher?.name ?: entity.type.name.replace('_',' '),11,0xFFFF9C89.toInt(),true),0)
        add(copy,text(entity.name,if(width<=180)20 else 28,Color.WHITE,true).apply { maxLines=3;ellipsize=android.text.TextUtils.TruncateAt.END },8)
        add(copy,text("Explore →",13,Color.WHITE,true),12)
        frame.addView(copy,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
        return frame
    }
    fun category(name: String,subtitle: String,iconName: String,accent: Int,onClick: ()->Unit): View {
        val line=row().apply { setPadding(dp(18),dp(18),dp(18),dp(18));minimumHeight=dp(96) }
        val badge=FrameLayout(context).apply { background=gradient(accent,palette.raised,20) }
        badge.addView(icon(iconName,name).apply { (this as? ImageView)?.imageTintList=ColorStateList.valueOf(Color.WHITE) },FrameLayout.LayoutParams(dp(26),dp(26),Gravity.CENTER))
        line.addView(badge,LinearLayout.LayoutParams(dp(56),dp(56)))
        val copy=column().apply { setPadding(dp(16),0,dp(8),0) }
        add(copy,text(name,20,bold=true),0);add(copy,text(subtitle,12,palette.muted),5)
        line.addView(copy,LinearLayout.LayoutParams(0,-2,1f));line.addView(icon("chevron",name))
        clickable(line,palette.surface,onClick)
        return line
    }
    fun loading(label: String="Loading the archive…"): View = column(20).apply {
        val bar=ProgressBar(context).apply { indeterminateTintList=ColorStateList.valueOf(palette.secondary) }
        addView(bar,LinearLayout.LayoutParams(dp(32),dp(32)).apply { gravity=Gravity.CENTER })
        add(this,text(label,14,palette.muted).apply { gravity=Gravity.CENTER },16)
    }
    fun clean(html: String): String = HtmlCompat.fromHtml(html.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"),""),HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
}

/** Keep faces near the top of portrait artwork visible in wide editorial slots. */
private class TopCropImageView(context: Context) : androidx.appcompat.widget.AppCompatImageView(context) {
    init { scaleType=ScaleType.MATRIX }

    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        alignArtwork()
    }

    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        alignArtwork()
    }

    private fun alignArtwork() {
        val artwork=drawable ?: return
        if(width<=0 || height<=0 || artwork.intrinsicWidth<=0 || artwork.intrinsicHeight<=0)return
        val scale=maxOf(width.toFloat()/artwork.intrinsicWidth,height.toFloat()/artwork.intrinsicHeight)
        imageMatrix=android.graphics.Matrix().apply {
            setScale(scale,scale)
            postTranslate((width-artwork.intrinsicWidth*scale)/2f,0f)
        }
    }
}
