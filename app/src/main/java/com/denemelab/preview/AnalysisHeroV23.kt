package com.denemelab.preview

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val V23Navy = Color(0xFF192F40)
internal val V23Teal = Color(0xFF178F80)
internal val V23Coral = Color(0xFFCD626D)
internal val V23Gold = Color(0xFFE4AE54)
internal val V23Muted = Color(0xFF738496)
internal val V23Background = Color(0xFFF4F7F8)
internal val V23Border = Color(0xFFE4EBEF)
internal val V23Mint = Color(0xFFBDF6DE)

@Composable
internal fun AnalysisHeroV23(
    snapshot: AnalysisSnapshot,
    last: ExamRecord,
    reduceMotion: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(27.dp),
        color = V23Navy
    ) {
        Box(
            Modifier.background(Brush.linearGradient(
                listOf(Color(0xFF1B3546), Color(0xFF172C3B), Color(0xFF174542))
            ))
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = Color(0xFF315D60)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(6.dp).background(V23Mint,CircleShape))
                            Spacer(Modifier.width(7.dp))
                            Text("SON DENEME", fontSize = 10.sp,
                                color = Color(0xFFE0F9F3),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = .8.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Rounded.AutoGraph, contentDescription = null,
                        modifier = Modifier.size(17.dp), tint = V23Mint)
                    Spacer(Modifier.width(5.dp))
                    Text(snapshot.questionLimit.toString() + " soru",
                        color = Color(0xFFADC6C8), fontSize = 11.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(LocalData.net(last.net), color = Color.White,
                            fontSize = 43.sp, fontWeight = FontWeight.ExtraBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("TOPLAM NET", color = Color(0xFF96B5BB),
                            letterSpacing = 1.2.sp,
                            fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(17.dp))
                        Text(last.name, color = Color(0xFFE6F0F1),
                            fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(LocalData.date(last.examDate),
                            color = Color(0xFF9AB3BD), fontSize = 10.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    HeroNetRingV23(last.net,snapshot.questionLimit,reduceMotion)
                }
                Spacer(Modifier.height(15.dp))
                Box(Modifier.fillMaxWidth().height(1.dp)
                    .background(Color(0xFF385362)))
                Spacer(Modifier.height(13.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroStatV23("DOĞRU",last.correct,V23Mint,
                        Color(0xFF224A49),Modifier.weight(1f))
                    HeroStatV23("YANLIŞ",last.wrong,Color(0xFFFFB0B7),
                        Color(0xFF503F4B),Modifier.weight(1f))
                    HeroStatV23("BOŞ",last.blank,Color(0xFFF2D18C),
                        Color(0xFF504A3C),Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HeroStatV23(label:String,value:Int,dot:Color,fill:Color,modifier:Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(13.dp),color = fill) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(dot,CircleShape))
            Spacer(Modifier.width(7.dp))
            Column {
                Text(value.toString(),fontSize = 18.sp,color = Color.White,
                    fontWeight = FontWeight.Bold)
                Text(label,fontSize = 9.sp,color = Color(0xFFD1DDDF),
                    fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HeroNetRingV23(net:Double, total:Int, reduceMotion:Boolean) {
    val count=total.coerceAtLeast(1)
    val progress=(net/count).toFloat().coerceIn(0f,1f)
    var visible by remember(net,count) { mutableFloatStateOf(0f) }
    LaunchedEffect(net,count) { visible=1f }
    val animated by animateFloatAsState(progress*visible,
        animationSpec=tween(if(reduceMotion) 0 else 700),label="NetRingV23")
    Box(Modifier.size(140.dp),contentAlignment=Alignment.Center) {
        Canvas(Modifier.size(137.dp).semantics {
            contentDescription = LocalData.net(net) + " net, " + count + " soru"
        }) {
            val thickness=11.dp.toPx()
            val inset=thickness/2f+5.dp.toPx()
            val sz=Size(size.width-inset*2,size.height-inset*2)
            val offset=Offset(inset,inset)
            drawArc(Color(0xFF415D69),145f,250f,false,
                offset,sz,style=Stroke(thickness,cap=StrokeCap.Round))
            drawArc(V23Mint,145f,250f*animated,false,
                offset,sz,style=Stroke(thickness,cap=StrokeCap.Round))
        }
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            Text((progress*100).toInt().toString()+"%",
                fontSize=26.sp,color=V23Mint,fontWeight=FontWeight.ExtraBold)
            Text("soru ölçeği",fontSize=9.sp,color=Color(0xFFAAC2C5))
        }
    }
}
