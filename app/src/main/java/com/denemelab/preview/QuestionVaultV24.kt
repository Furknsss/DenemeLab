package com.denemelab.preview

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val QInk=Color(0xFF111820)
private val QGrey=Color(0xFF718093)
private val QMint=Color(0xFF08D5A8)
private val QBlue=Color(0xFF5779F7)
private val QRed=Color(0xFFF05A76)
private val QGold=Color(0xFFF2B22E)
private val QBorder=Color(0xFFE5EAF0)

@Composable
fun QuestionVaultV24(
    questions:List<QuestionRecord>,
    onAdd:()->Unit,
    onUpdate:(QuestionRecord,Boolean)->Unit,
    onDelete:(QuestionRecord)->Boolean
) {
    val context=LocalContext.current
    var selected by remember { mutableStateOf("Bekleyen") }
    var search by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<QuestionRecord?>(null) }
    var deleting by remember { mutableStateOf<QuestionRecord?>(null) }
    val filtered=questions.filter {
        (selected=="Tümü" || (selected=="Bekleyen"&&!it.completed) ||
            (selected=="Tamamlanan"&&it.completed)) &&
            (it.title+" "+it.subject+" "+it.topic+" "+it.source)
                .contains(search,ignoreCase=true)
    }
    LazyVerticalGrid(
        columns=GridCells.Adaptive(280.dp),
        modifier=Modifier.fillMaxSize().background(Color(0xFFF5F7FA)),
        contentPadding=PaddingValues(start=18.dp,end=18.dp,top=16.dp,bottom=120.dp),
        horizontalArrangement=Arrangement.spacedBy(12.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ) {
        item(span={GridItemSpan(maxLineSpan)}) {
            Column {
                Text("KİŞİSEL SORU BANKAN",fontSize=10.sp,
                    fontWeight=FontWeight.ExtraBold,color=Color(0xFF00A98A),
                    letterSpacing=1.1.sp)
                Spacer(Modifier.height(5.dp))
                Text("Soru Kumbaram",fontSize=27.sp,
                    fontWeight=FontWeight.ExtraBold,color=QInk)
                Spacer(Modifier.height(4.dp))
                Text("Fotoğraflı arşiv · Takip · Tekrar",
                    fontSize=12.sp,color=QGrey)
            }
        }
        item(span={GridItemSpan(maxLineSpan)}) {
            Surface(shape=RoundedCornerShape(22.dp),color=QInk) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Bookmarks,null,tint=QMint,
                            modifier=Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("SORU KUMBARASI",color=Color(0xFFB5FFF0),
                            fontSize=10.sp,fontWeight=FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(questions.size.toString()+" kayıt",
                            fontSize=11.sp,color=Color(0xFFC4CDD8))
                    }
                    Spacer(Modifier.height(17.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                        QuestionCounter("TEKRAR",questions.count{!it.completed},
                            Color(0xFF174C40),QMint,Modifier.weight(1f))
                        QuestionCounter("TAMAMLANAN",questions.count{it.completed},
                            Color(0xFF303A62),Color(0xFF9BACFF),Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(14.dp))
                    Surface(onClick=onAdd,color=QMint,
                        shape=RoundedCornerShape(15.dp)) {
                        Row(Modifier.fillMaxWidth().padding(vertical=14.dp),
                            horizontalArrangement=Arrangement.Center,
                            verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CameraAlt,null,
                                modifier=Modifier.size(20.dp),tint=QInk)
                            Spacer(Modifier.width(9.dp))
                            Text("Fotoğrafla soru ekle",color=QInk,
                                fontSize=13.sp,fontWeight=FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
        item(span={GridItemSpan(maxLineSpan)}) {
            Column {
                OutlinedTextField(value=search,onValueChange={search=it.take(120)},
                    modifier=Modifier.fillMaxWidth(),
                    leadingIcon={Icon(Icons.Rounded.Search,null,tint=QBlue)},
                    placeholder={Text("Ders, konu, kaynak veya soru ara",fontSize=12.sp)},
                    singleLine=true,shape=RoundedCornerShape(14.dp))
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    listOf("Bekleyen","Tamamlanan","Tümü").forEach { name ->
                        Surface(onClick={selected=name},
                            shape=CircleShape,
                            color=if(selected==name)QInk else Color.White,
                            border=BorderStroke(1.dp,if(selected==name)QInk else QBorder)){
                            Text(name,modifier=Modifier.padding(horizontal=14.dp,vertical=10.dp),
                                fontSize=11.sp,fontWeight=FontWeight.Bold,
                                color=if(selected==name)QMint else QGrey)
                        }
                    }
                }
            }
        }
        if(filtered.isEmpty()){
            item(span={GridItemSpan(maxLineSpan)}){
                Surface(color=Color.White,shape=RoundedCornerShape(19.dp)){
                    Column(Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment=Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.CameraAlt,null,modifier=Modifier.size(36.dp),
                            tint=QBlue)
                        Spacer(Modifier.height(12.dp))
                        Text(if(questions.isEmpty())"İlk sorunu fotoğrafla kaydet"
                            else "Bu filtrede soru yok",fontWeight=FontWeight.Bold,
                            fontSize=15.sp,color=QInk)
                        Spacer(Modifier.height(6.dp))
                        Text("Kırpılan soru, konu bilgisi ve tekrar durumu birlikte saklanır.",
                            color=QGrey,fontSize=11.sp,textAlign=TextAlign.Center)
                    }
                }
            }
        }else{
            items(filtered,key={it.id}) { question ->
                Surface(color=Color.White,shape=RoundedCornerShape(18.dp),
                    border=BorderStroke(1.dp,QBorder),tonalElevation=1.dp) {
                    Column(Modifier.padding(13.dp)) {
                        QuestionPhotoStore.imageUri(context,question.imageFile)?.let { photo ->
                            Surface(onClick={detail=question},shape=RoundedCornerShape(12.dp),
                                color=Color(0xFFF2F5F7)) {
                                AsyncImage(model=photo,contentDescription="Soru görseli",
                                    modifier=Modifier.fillMaxWidth().height(183.dp),
                                    contentScale=ContentScale.Fit)
                            }
                            Spacer(Modifier.height(11.dp))
                        }
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Surface(shape=RoundedCornerShape(8.dp),
                                color=if(question.completed)Color(0xFFE0FFF2)
                                    else Color(0xFFFFEBEF)){
                                Text(if(question.completed)"TAMAMLANDI" else question.outcome.uppercase(),
                                    color=if(question.completed)Color(0xFF008B70) else QRed,
                                    fontSize=9.sp,fontWeight=FontWeight.Bold,
                                    modifier=Modifier.padding(horizontal=9.dp,vertical=6.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick={deleting=question},modifier=Modifier.size(33.dp)){
                                Icon(Icons.Rounded.DeleteOutline,"Sil",tint=QGrey,
                                    modifier=Modifier.size(19.dp))
                            }
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(question.title,color=QInk,fontSize=15.sp,
                            fontWeight=FontWeight.Bold,maxLines=2,
                            overflow=TextOverflow.Ellipsis)
                        Spacer(Modifier.height(5.dp))
                        Text(question.subject+(if(question.topic.isNotBlank())
                            " · "+question.topic else ""),
                            color=QGrey,fontSize=11.sp,maxLines=2)
                        if(question.source.isNotBlank()){
                            Spacer(Modifier.height(4.dp))
                            Text(question.source,color=QBlue,fontSize=10.sp,
                                maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(11.dp))
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            repeat(2){i->
                                Box(Modifier.weight(1f).height(6.dp)
                                    .padding(end=if(i==0)4.dp else 0.dp)
                                    .background(if(i<question.correctStreak)QMint
                                        else Color(0xFFE7EBEF),CircleShape))
                            }
                            Spacer(Modifier.width(9.dp))
                            Text(question.correctStreak.toString()+"/2",
                                fontWeight=FontWeight.Bold,color=QInk,fontSize=11.sp)
                        }
                        if(!question.completed){
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                QuestionAction("Doğru çözdüm",Color(0xFFE2FFF3),
                                    Color(0xFF008C70),Modifier.weight(1f)){
                                    onUpdate(question,true)
                                }
                                QuestionAction("Tekrar yanlış",Color(0xFFFFEAF0),
                                    QRed,Modifier.weight(1f)){onUpdate(question,false)}
                            }
                        }
                    }
                }
            }
        }
    }
    detail?.let { question ->
        Dialog(onDismissRequest={detail=null},
            properties=DialogProperties(usePlatformDefaultWidth=false)){
            Surface(Modifier.fillMaxWidth(.97f),shape=RoundedCornerShape(23.dp),
                color=Color.White) {
                Column(Modifier.padding(14.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Text(question.title,Modifier.weight(1f),color=QInk,
                            fontWeight=FontWeight.Bold,fontSize=16.sp)
                        IconButton(onClick={detail=null}){
                            Icon(Icons.Rounded.Close,"Kapat",tint=QInk)
                        }
                    }
                    QuestionPhotoStore.imageUri(context,question.imageFile)?.let{
                        AsyncImage(model=it,contentDescription="Sorunun tamamı",
                            modifier=Modifier.fillMaxWidth().heightIn(max=510.dp),
                            contentScale=ContentScale.Fit)
                    }
                    Text(question.subject+" · "+question.topic,
                        fontSize=11.sp,color=QGrey)
                }
            }
        }
    }
    deleting?.let { question ->
        AlertDialog(onDismissRequest={deleting=null},
            title={Text("Soruyu sil?",fontWeight=FontWeight.Bold)},
            text={Text("Soru ve fotoğraf kalıcı olarak silinecek.")},
            confirmButton={TextButton(onClick={
                if(onDelete(question)) deleting=null
                else Toast.makeText(context,"Soru silinemedi.",Toast.LENGTH_LONG).show()
            }){Text("Sil",color=QRed)}},
            dismissButton={TextButton(onClick={deleting=null}){Text("Vazgeç")}}
        )
    }
}

@Composable
private fun QuestionCounter(text:String,count:Int,color:Color,accent:Color,modifier:Modifier){
    Surface(modifier=modifier,color=color,shape=RoundedCornerShape(13.dp)){
        Column(Modifier.padding(12.dp)){
            Text(text,color=accent,fontSize=9.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(count.toString(),color=Color.White,fontSize=27.sp,
                fontWeight=FontWeight.ExtraBold)
        }
    }
}
@Composable
private fun QuestionAction(text:String,bg:Color,fg:Color,modifier:Modifier,onClick:()->Unit){
    Surface(onClick=onClick,modifier=modifier,color=bg,shape=RoundedCornerShape(10.dp)){
        Text(text,color=fg,textAlign=TextAlign.Center,fontSize=10.sp,
            fontWeight=FontWeight.Bold,modifier=Modifier.padding(vertical=11.dp,horizontal=5.dp))
    }
}

@Composable
fun QuestionEntryV24(onDismiss:()->Unit,onSave:(QuestionRecord)->Boolean){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var source by remember { mutableStateOf<Uri?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var title by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("TYT Matematik") }
    var topic by remember { mutableStateOf("") }
    var publisher by remember { mutableStateOf("") }
    var outcome by remember { mutableStateOf("Yanlış") }
    var busy by remember { mutableStateOf(false) }
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){success->
        if(success)source=cameraUri else cameraFile?.delete()
    }
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
        if(uri!=null)source=uri
    }
    fun cameraStart(){
        runCatching{
            cameraFile?.delete()
            val (uri,file)=QuestionPhotoStore.cameraTarget(context)
            cameraUri=uri
            cameraFile=file
            camera.launch(uri)
        }.onFailure{
            Toast.makeText(context,it.message?:"Kamera açılamadı.",Toast.LENGTH_LONG).show()
        }
    }
    Dialog(onDismissRequest={if(!busy){cameraFile?.delete();onDismiss()}},
        properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxWidth(.97f).fillMaxHeight(.93f),
            color=Color(0xFFF8FAFC),shape=RoundedCornerShape(24.dp)){
            Column {
                Row(Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text("Kumbaraya soru ekle",fontSize=20.sp,
                            color=QInk,fontWeight=FontWeight.ExtraBold)
                        Text("Fotoğraf · Kırpma · Kayıt",fontSize=11.sp,color=QGrey)
                    }
                    IconButton(enabled=!busy,onClick={cameraFile?.delete();onDismiss()}){
                        Icon(Icons.Rounded.Close,"Kapat",tint=QInk)
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal=16.dp),
                    verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Surface(color=Color.White,shape=RoundedCornerShape(18.dp),
                        border=BorderStroke(1.dp,QBorder)){
                        Column(Modifier.padding(12.dp)) {
                            if(image!=null) {
                                Image(image!!.asImageBitmap(),"Kırpılmış soru fotoğrafı",
                                    modifier=Modifier.fillMaxWidth().height(170.dp),
                                    contentScale=ContentScale.Fit)
                                Spacer(Modifier.height(8.dp))
                                Text("Soru görseli hazır",color=Color(0xFF008B70),
                                    fontWeight=FontWeight.Bold,fontSize=11.sp)
                            } else {
                                Row(verticalAlignment=Alignment.CenterVertically){
                                    Icon(Icons.Rounded.CameraAlt,null,tint=QBlue,
                                        modifier=Modifier.size(29.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column{
                                        Text("Sorunun fotoğrafını ekle",fontSize=13.sp,
                                            fontWeight=FontWeight.Bold,color=QInk)
                                        Text("Numara ve şekilleri kırpmadan sakla",
                                            fontSize=10.sp,color=QGrey)
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                QuestionAction("Kamera",QMint,QInk,Modifier.weight(1f)){cameraStart()}
                                QuestionAction("Galeriden seç",Color(0xFFE9ECFF),
                                    QBlue,Modifier.weight(1f)){gallery.launch("image/*")}
                            }
                        }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        listOf("Yanlış" to QRed,"Boş" to QGold).forEach { (label,color) ->
                            Surface(onClick={outcome=label},shape=RoundedCornerShape(10.dp),
                                color=if(outcome==label)color else Color.White,
                                border=BorderStroke(1.dp,if(outcome==label)color else QBorder)){
                                Text(label,modifier=Modifier.padding(horizontal=18.dp,vertical=11.dp),
                                    fontSize=12.sp,fontWeight=FontWeight.Bold,
                                    color=if(outcome==label)Color.White else QGrey)
                            }
                        }
                    }
                    Text("DERS",color=QBlue,fontWeight=FontWeight.Bold,fontSize=10.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        listOf("TYT Türkçe","TYT Matematik","TYT Geometri","TYT Fen",
                            "TYT Sosyal","AYT Matematik","AYT Fizik","AYT Kimya",
                            "AYT Biyoloji").forEach { label ->
                            Surface(onClick={subject=label},shape=CircleShape,
                                color=if(subject==label)QInk else Color.White){
                                Text(label,modifier=Modifier.padding(horizontal=12.dp,vertical=10.dp),
                                    fontSize=10.sp,fontWeight=FontWeight.Bold,
                                    color=if(subject==label)QMint else QGrey)
                            }
                        }
                    }
                    QuestionField("Soru başlığı / numarası (isteğe bağlı)",title){title=it.take(100)}
                    QuestionField("Konu (isteğe bağlı)",topic){topic=it.take(90)}
                    QuestionField("Kaynak / yayın (isteğe bağlı)",publisher){publisher=it.take(90)}
                    Text("Soru görseli ve tekrar bilgisi saklanır. İki doğru tekrardan sonra tamamlanır.",
                        fontSize=10.sp,color=QGrey)
                    Spacer(Modifier.height(5.dp))
                }
                Surface(color=Color.White,tonalElevation=3.dp){
                    Button(onClick={
                        busy=true
                        scope.launch{
                            var file=""
                            try {
                                image?.let{
                                    file=withContext(Dispatchers.IO){
                                        QuestionPhotoStore.persist(context,it)
                                    }
                                }
                                val now=System.currentTimeMillis()
                                val saved=QuestionRecord(now,title.trim().ifBlank{"Fotoğraflı soru"},
                                    subject,topic.trim(),publisher.trim(),outcome,0,now,file)
                                if(!onSave(saved))
                                    throw IllegalStateException("Soru kaydedilemedi.")
                                cameraFile?.delete()
                            }catch(e:Exception){
                                if(file.isNotBlank())QuestionPhotoStore.discard(context,file)
                                Toast.makeText(context,e.message?:"Kaydedilemedi.",
                                    Toast.LENGTH_LONG).show()
                            }finally{busy=false}
                        }
                    },enabled=!busy && (image!=null || title.isNotBlank()),
                        modifier=Modifier.fillMaxWidth().padding(14.dp).height(52.dp),
                        shape=RoundedCornerShape(15.dp),
                        colors=ButtonDefaults.buttonColors(
                            containerColor=QMint,contentColor=QInk,
                            disabledContainerColor=Color(0xFFE8EDF1),
                            disabledContentColor=QGrey)){
                        if(busy) CircularProgressIndicator(
                            modifier=Modifier.size(18.dp),strokeWidth=2.dp,color=QInk)
                        else Icon(Icons.Rounded.CheckCircle,null,modifier=Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if(busy)"Kaydediliyor…" else "Kumbarama kaydet",
                            fontWeight=FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
    source?.let {
        QuestionCropV24(it,onDismiss={
            source=null
            cameraFile?.delete()
        },onSelected={bitmap->
            image=bitmap
            source=null
            cameraFile?.delete()
        })
    }
}
@Composable
private fun QuestionField(label:String,value:String,onValueChange:(String)->Unit){
    OutlinedTextField(value=value,onValueChange=onValueChange,
        modifier=Modifier.fillMaxWidth(),label={Text(label,fontSize=11.sp)},
        singleLine=true,shape=RoundedCornerShape(13.dp))
}
