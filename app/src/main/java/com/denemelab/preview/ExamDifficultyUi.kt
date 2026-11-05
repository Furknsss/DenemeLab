package com.denemelab.preview

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

object ExamDifficulty {
    val values = listOf("Kolay","Kolay-Orta","Orta","Orta-Zor","Zor")
    val colors = listOf(
        Color(0xFF55A674),Color(0xFF61A9D9),Color(0xFFE1B959),
        Color(0xFFD89047),Color(0xFF9B70AE)
    )
    fun index(text: String): Int = values.indexOf(text)
}

@Composable
fun DifficultyPicker(
    selected: String, onSelected: (String) -> Unit, reduceMotion: Boolean = false
) {
    Column(Modifier.fillMaxWidth()) {
        Text("Deneme zorluğu", color = Color(0xFF141820),fontSize = 16.sp,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text("Sana ne kadar zor geldi? Analizde öznel bağlam olarak gösterilir.",
            color = Color(0xFF79818F),fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        DifficultyArc(selected,reduceMotion)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ExamDifficulty.values.forEachIndexed { idx, label ->
                val active = selected == label
                Surface(onClick = { onSelected(label) },
                    color = if(active) ExamDifficulty.colors[idx].copy(alpha = 0.15f)
                        else Color(0xFFF6F8FB), shape = CircleShape,
                    border = if(active) BorderStroke(1.dp,ExamDifficulty.colors[idx]) else null) {
                    Text(label,Modifier.padding(horizontal=11.dp,vertical=9.dp),
                        color = if(active) Color(0xFF151820) else Color(0xFF727783),
                        fontWeight=if(active) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun DifficultyArc(selected:String, reduceMotion:Boolean) {
    val level = ExamDifficulty.index(selected)
    val target = if(level >= 0) 180f + (level+0.5f)*36f else 270f
    val angle by animateFloatAsState(target,
        animationSpec=tween(if(reduceMotion) 0 else 520),label="DifficultyPointer")
    Column(Modifier.fillMaxWidth(), horizontalAlignment=Alignment.CenterHorizontally) {
        Canvas(Modifier.width(220.dp).height(126.dp)) {
            val bottom = size.height - 9.dp.toPx()
            val center = Offset(size.width/2f,bottom)
            val radius = minOf(size.width/2f-12.dp.toPx(),bottom-8.dp.toPx())
            val area = Size(radius*2,radius*2)
            val corner = Offset(center.x-radius,center.y-radius)
            val stroke = 14.dp.toPx()
            ExamDifficulty.colors.forEachIndexed { index,color ->
                drawArc(color,180f+index*36f+1.3f,33.4f,
                    useCenter=false,topLeft=corner,size=area,
                    style=Stroke(stroke,cap=StrokeCap.Butt))
            }
            val a = Math.toRadians(angle.toDouble())
            val pointer = Offset(
                center.x+cos(a).toFloat()*radius*0.78f,
                center.y+sin(a).toFloat()*radius*0.78f
            )
            drawLine(Color(0xFF141820),center,pointer,
                strokeWidth=3.dp.toPx(),cap=StrokeCap.Round)
            drawCircle(Color(0xFF141820),6.dp.toPx(),center)
            drawCircle(Color.White,2.dp.toPx(),center)
        }
        Text(if(level>=0) selected else "Seçilmedi",color=Color(0xFF141820),
            fontSize=14.sp,fontWeight=FontWeight.SemiBold)
        Spacer(Modifier.height(11.dp))
    }
}

@Composable
fun DifficultyMiniBadge(level:String) {
    val index=ExamDifficulty.index(level)
    if(index<0) return
    val c=ExamDifficulty.colors[index]
    Surface(shape=RoundedCornerShape(9.dp),color=c.copy(alpha=0.13f)) {
        Row(Modifier.padding(horizontal=8.dp,vertical=6.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Surface(Modifier.size(7.dp),color=c,shape=CircleShape) {}
            Spacer(Modifier.width(5.dp))
            Text(level,color=Color(0xFF474747),fontSize=10.sp,
                fontWeight=FontWeight.SemiBold)
        }
    }
}
