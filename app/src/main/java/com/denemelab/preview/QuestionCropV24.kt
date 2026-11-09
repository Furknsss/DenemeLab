package com.denemelab.preview

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CropMint = Color(0xFF00D9AC)
private val CropInk = Color(0xFF12171F)

private data class Selection(val l: Float = .05f, val t: Float = .06f,
    val r: Float = .95f, val b: Float = .94f) {
    fun drag(dx: Float, dy: Float, index: Int): Selection {
        if (index == -1) {
            val moveX = dx.coerceIn(-l, 1f-r)
            val moveY = dy.coerceIn(-t, 1f-b)
            return copy(l=l+moveX, t=t+moveY, r=r+moveX, b=b+moveY)
        }
        return when(index) {
            0 -> copy(l=(l+dx).coerceIn(0f,r-.14f),t=(t+dy).coerceIn(0f,b-.14f))
            1 -> copy(r=(r+dx).coerceIn(l+.14f,1f),t=(t+dy).coerceIn(0f,b-.14f))
            2 -> copy(l=(l+dx).coerceIn(0f,r-.14f),b=(b+dy).coerceIn(t+.14f,1f))
            else -> copy(r=(r+dx).coerceIn(l+.14f,1f),b=(b+dy).coerceIn(t+.14f,1f))
        }
    }
}

@Composable
fun QuestionCropV24(
    source: Uri, onDismiss: () -> Unit, onSelected: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var image by remember(source) { mutableStateOf<Bitmap?>(null) }
    var error by remember(source) { mutableStateOf("") }
    var selection by remember(source) { mutableStateOf(Selection()) }
    LaunchedEffect(source) {
        runCatching { withContext(Dispatchers.IO) {
            QuestionPhotoStore.decodePreview(context, source)
        } }.onSuccess { image=it }.onFailure { error=it.message ?: "Fotoğraf okunamadı" }
    }
    Dialog(onDismissRequest=onDismiss,
        properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(
            modifier=Modifier.fillMaxWidth(.98f).fillMaxHeight(.92f),
            shape=RoundedCornerShape(24.dp), color=CropInk
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick=onDismiss) {
                        Icon(Icons.Rounded.ArrowBack,"Geri",tint=Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Soru çerçevesi",color=Color.White,
                            fontWeight=FontWeight.ExtraBold,fontSize=18.sp)
                        Text("Numarayı ve şekilleri içeride bırak",
                            color=Color(0xFFAAB8C3),fontSize=11.sp)
                    }
                    IconButton(enabled=image!=null,onClick={
                        image?.let { image=QuestionPhotoStore.rotateRight(it);selection=Selection() }
                    }) {
                        Icon(Icons.Rounded.RotateRight,"Döndür",tint=CropMint)
                    }
                }
                BoxWithConstraints(
                    Modifier.weight(1f).fillMaxWidth().padding(12.dp),
                    contentAlignment=Alignment.Center
                ) {
                    val bitmap=image
                    if(bitmap == null) {
                        if(error.isEmpty()) CircularProgressIndicator(color=CropMint)
                        else Text(error,color=Color(0xFFFFA3B5))
                    } else {
                        val ratio=bitmap.width.toFloat()/bitmap.height
                        val width=minOf(maxWidth,maxHeight*ratio)
                        var dimensions by remember(bitmap) { mutableStateOf(IntSize.Zero) }
                        var active by remember(bitmap) { mutableIntStateOf(-2) }
                        Box(Modifier.width(width).aspectRatio(ratio)
                            .onSizeChanged { dimensions=it }) {
                            Image(bitmap.asImageBitmap(),contentDescription="Fotoğraftaki soru",
                                modifier=Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds)
                            Canvas(Modifier.matchParentSize().pointerInput(bitmap,dimensions) {
                                detectDragGestures(
                                    onDragStart={ pos ->
                                        val w=dimensions.width.toFloat().coerceAtLeast(1f)
                                        val h=dimensions.height.toFloat().coerceAtLeast(1f)
                                        val handles=listOf(
                                            Offset(selection.l*w,selection.t*h),
                                            Offset(selection.r*w,selection.t*h),
                                            Offset(selection.l*w,selection.b*h),
                                            Offset(selection.r*w,selection.b*h)
                                        )
                                        val found=handles.indexOfFirst {
                                            (it-pos).getDistance()<45.dp.toPx()
                                        }
                                        active=if(found>=0) found else if(
                                            pos.x in selection.l*w..selection.r*w &&
                                            pos.y in selection.t*h..selection.b*h) -1 else -2
                                    },
                                    onDragEnd={active=-2},
                                    onDragCancel={active=-2},
                                    onDrag={change,movement ->
                                        change.consume()
                                        if(active != -2 && dimensions.width>0 && dimensions.height>0)
                                            selection=selection.drag(
                                                movement.x/dimensions.width,
                                                movement.y/dimensions.height,active)
                                    }
                                )
                            }) {
                                val l=selection.l*size.width
                                val t=selection.t*size.height
                                val r=selection.r*size.width
                                val b=selection.b*size.height
                                val shade=Color(0x9E000000)
                                drawRect(shade,Offset.Zero,Size(size.width,t))
                                drawRect(shade,Offset(0f,b),Size(size.width,size.height-b))
                                drawRect(shade,Offset(0f,t),Size(l,b-t))
                                drawRect(shade,Offset(r,t),Size(size.width-r,b-t))
                                drawRect(Color.White,Offset(l,t),Size(r-l,b-t),
                                    style=Stroke(width=2.dp.toPx()))
                                listOf(Offset(l,t),Offset(r,t),Offset(l,b),Offset(r,b))
                                    .forEach { p ->
                                        drawCircle(CropMint,11.dp.toPx(),center=p)
                                        drawCircle(Color.White,4.dp.toPx(),center=p)
                                    }
                            }
                        }
                    }
                }
                Column(Modifier.padding(16.dp)) {
                    Text("Yeşil köşeleri sürükleyerek yalnızca soruyu seç",
                        color=Color(0xFFBEC9D4),fontSize=11.sp,textAlign=TextAlign.Center,
                        modifier=Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Button(onClick={
                        image?.let {
                            runCatching { QuestionPhotoStore.crop(it,
                                selection.l,selection.t,selection.r,selection.b) }
                                .onSuccess(onSelected).onFailure { failure ->
                                    Toast.makeText(context,failure.message?:"Kırpma başarısız",
                                        Toast.LENGTH_LONG).show()
                                }
                        }
                    },enabled=image!=null,modifier=Modifier.fillMaxWidth().height(52.dp),
                        shape=RoundedCornerShape(15.dp),
                        colors=ButtonDefaults.buttonColors(containerColor=CropMint,
                            contentColor=CropInk)) {
                        Icon(Icons.Rounded.Crop,null,modifier=Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Bu soruyu kullan",fontWeight=FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}
