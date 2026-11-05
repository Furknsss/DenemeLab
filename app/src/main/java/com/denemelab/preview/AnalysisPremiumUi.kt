package com.denemelab.preview

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private val Black = Color(0xFF111111)
private val Grey = Color(0xFF747474)
private val Pale = Color(0xFFF6F6F6)
private val Border = Color(0xFFE9E9E9)
private val Off = Color(0xFFD4D4D4)
private val White = Color.White

@Composable
fun AnalysisDashboard(
    exams: List<ExamRecord>,
    category: String,
    onCategory: (String) -> Unit,
    onAdd: () -> Unit,
    reduceMotion: Boolean
) {
    val ctx = LocalContext.current
    var focusRecords by remember { mutableStateOf(FocusJournal.load(ctx)) }
    var selectedScope by remember(category) { mutableStateOf("") }
    var plotCount by remember { mutableIntStateOf(5) }
    var expandedTopic by remember { mutableStateOf<String?>(null) }
    val scopes = AnalysisEngine.scopes(exams, category)
    val current = scopes.firstOrNull { it.examType + "|" + it.detail == selectedScope }
        ?: scopes.firstOrNull() ?: AnalysisScope(category,"TYT")
    val snapshot = AnalysisEngine.analyze(exams, current)
    val latest = snapshot.last

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(start = 20.dp,end = 20.dp,top = 12.dp,bottom = 122.dp)) {
        Text("Analiz", color = Black, fontWeight = FontWeight.Bold, fontSize = 30.sp)
        Spacer(Modifier.height(6.dp))
        Text("Netini büyüt. Eksiklerini kalıcı kapat.", color = Grey, fontSize = 13.sp)
        Spacer(Modifier.height(23.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnalysisPill("Genel denemeler",category == "Genel",Modifier.weight(1f)) {
                onCategory("Genel")
            }
            AnalysisPill("Branş denemeleri",category == "Branş",Modifier.weight(1f)) {
                onCategory("Branş")
            }
        }
        Spacer(Modifier.height(13.dp))
        if (scopes.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                scopes.forEach { scope ->
                    val key = scope.examType + "|" + scope.detail
                    val label = if (scope.category == "Branş") "${scope.examType} · ${scope.detail}"
                        else if (scope.examType == "AYT" && scope.detail.isBlank()) "AYT · Belirsiz"
                        else scope.label
                    AnalysisPill(label, scope == current, Modifier) {
                        selectedScope = key
                        expandedTopic = null
                    }
                }
            }
            Spacer(Modifier.height(21.dp))
        }

        if (latest == null) {
            DashboardCard {
                Spacer(Modifier.height(10.dp))
                Text("Henüz karşılaştırılabilir deneme yok",
                    color = Black,fontSize = 19.sp,fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("Bu sınav türünden bir deneme eklediğinde net pusulan, konu haritan ve gelişim grafiğin açılır.",
                    color = Grey,fontSize = 13.sp,lineHeight = 19.sp)
                Spacer(Modifier.height(18.dp))
                DarkAction("İlk denemeni ekle",onAdd)
            }
            return@Column
        }

        DashboardCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NET PUSULASI",fontSize = 11.sp,letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.Bold,color = Grey,modifier = Modifier.weight(1f))
                Text("${snapshot.history.size} deneme",color = Grey,fontSize = 11.sp)
            }
            Spacer(Modifier.height(1.dp))
            SpeedGauge(latest.net,snapshot.questionLimit,reduceMotion)
            HorizontalDivider(color = Border)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricLabel("Son 3 ort.",snapshot.average?.let { LocalData.net(it) } ?: "—",
                    Modifier.weight(1f))
                MetricLabel("Son 5 aralık",
                    if (snapshot.recent.size < 2) "—" else
                        "${LocalData.net(snapshot.minNet ?: 0.0)}–${LocalData.net(snapshot.maxNet ?: 0.0)}",
                    Modifier.weight(1f))
            }
            Spacer(Modifier.height(15.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DistributionDonut(latest.correct,latest.wrong,latest.blank,reduceMotion)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Bu denemedeki kayıp alanı",fontSize = 13.sp,
                        color = Black,fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(7.dp))
                    Text("${latest.wrong} yanlış · ${latest.blank} boş",
                        color = Grey,fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    val possible = latest.wrong * 1.25 + latest.blank
                    Text("Tamamı doğru olsa +${LocalData.net(possible)} net",
                        fontSize = 11.sp,color = Black,fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(3.dp))
                    Text("Teorik üst sınır; net artışı garantisi değildir.",
                        fontSize = 10.sp,color = Grey,lineHeight = 14.sp)
                }
            }
        }
        Spacer(Modifier.height(23.dp))

        SectionHeader("Net ritmin","Aynı sınav türünde ölçülen gerçek netlerin")
        DashboardCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Gelişim çizgisi",fontWeight = FontWeight.SemiBold,color = Black,fontSize = 14.sp)
                    Spacer(Modifier.height(3.dp))
                    Text("Yayın zorluğu değişebilir; tek denemeye göre karar verme.",
                        color = Grey,fontSize = 10.sp,lineHeight = 14.sp)
                }
                if (snapshot.history.size > 5) {
                    AnalysisPill(if(plotCount == 5) "Son 5" else "Son 10",
                        false,Modifier) { plotCount = if(plotCount == 5) 10 else 5 }
                }
            }
            Spacer(Modifier.height(12.dp))
            val plotted = snapshot.history.takeLast(plotCount)
            ModernTrendChart(plotted,reduceMotion)
            if (snapshot.previousAverage != null && snapshot.trendDelta != null) {
                Spacer(Modifier.height(13.dp))
                Text(
                    "Son ${minOf(3,snapshot.history.size)} deneme ortalaması, önceki döneme göre " +
                        signedNet(snapshot.trendDelta!!) + " net",
                    color = Black,fontSize = 12.sp,fontWeight = FontWeight.Medium)
            } else {
                Spacer(Modifier.height(9.dp))
                Text("Dönem karşılaştırması için en az 4–5 karşılaştırılabilir deneme gerekir.",
                    color = Grey,fontSize = 11.sp,lineHeight = 15.sp)
            }
        }
        Spacer(Modifier.height(23.dp))

        SectionHeader("Ders röntgeni","Net kaybının hangi derste biriktiğini gör")
        if (snapshot.subjectInsights.isEmpty()) {
            DashboardCard {
                Text("Ders kırılımı yok",fontSize = 15.sp,color = Black,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(7.dp))
                Text("Bu denemenin yalnızca toplam neti kaydedilmiş. Ders bazlı kayıt eklediğinde burası otomatik dolacak.",
                    color = Grey,fontSize = 12.sp,lineHeight = 17.sp)
                Spacer(Modifier.height(13.dp))
                ThinAction("Ders sonuçlu deneme ekle",onAdd)
            }
        } else {
            DashboardCard {
                if (snapshot.subjectSource?.id != latest.id) {
                    Text("Ders ayrıntıları ${LocalData.date(snapshot.subjectSource?.examDate ?: 0)} tarihli son ayrıntılı kayıttan alındı.",
                        color = Grey,fontSize = 11.sp,lineHeight = 15.sp)
                    Spacer(Modifier.height(12.dp))
                }
                snapshot.subjectInsights.forEachIndexed { index, subject ->
                    SubjectLossLine(subject,reduceMotion)
                    if (index != snapshot.subjectInsights.lastIndex) {
                        HorizontalDivider(color = Border,modifier = Modifier.padding(vertical = 13.dp))
                    }
                }
                Spacer(Modifier.height(13.dp))
                Text("Daireler ders netinin soru sayısına oranını gösterir. Net değişimi yalnızca önceki ders kaydı varsa hesaplanır.",
                    color = Grey,fontSize = 10.sp,lineHeight = 14.sp)
            }
        }
        Spacer(Modifier.height(23.dp))

        SectionHeader("Odaklanman gerekenler","Tekrar eden konular önce; kanıta dayalı çalışma")
        if (snapshot.priorities.isEmpty()) {
            DashboardCard {
                Text("Henüz konu bazlı kayıt yok",color = Black,fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(7.dp))
                Text("Yanlış ve boşların konularını detaylandır. Hangi konuya önce çalışman gerektiğini gerçek kayıtlardan çıkaracağız.",
                    color = Grey,fontSize = 12.sp,lineHeight = 17.sp)
                Spacer(Modifier.height(15.dp))
                if (snapshot.subjectInsights.isNotEmpty()) {
                    Text("Son ders kaydında en yüksek kayıp alanları",
                        color = Black,fontWeight = FontWeight.Medium,fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    snapshot.subjectInsights.sortedByDescending { it.theoreticalRoom }
                        .take(3).forEach { lesson ->
                            Text("• ${lesson.name}: ${lesson.wrong} yanlış · ${lesson.blank} boş",
                                color = Grey,fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 5.dp))
                        }
                    Spacer(Modifier.height(10.dp))
                }
                ThinAction("Konu detaylı deneme ekle",onAdd)
            }
        } else {
            snapshot.priorities.take(3).forEachIndexed { index, topic ->
                val topicId = FocusJournal.focusId(current,topic)
                val completed = focusRecords.firstOrNull { it.id == topicId }
                FocusPriorityCard(
                    index = index, topic = topic,
                    expand = expandedTopic == topicId,
                    completion = completed,
                    snapshot = snapshot,
                    onExpand = { expandedTopic = if(expandedTopic == topicId) null else topicId },
                    onComplete = {
                        val new = FocusCompletion(
                            id = topicId, topicName = topic.label,
                            lessonName = topic.lessonName,
                            completedAt = System.currentTimeMillis(),
                            baselineExamDate = latest.examDate,
                            baselineExamNet = latest.net,
                            baselineExamId = latest.id
                        )
                        val updated = listOf(new) + focusRecords.filterNot { it.id == topicId }
                        if (FocusJournal.save(ctx,updated)) {
                            focusRecords = updated
                        } else Toast.makeText(ctx,"Çalışma kaydı kaydedilemedi.",
                            Toast.LENGTH_LONG).show()
                    }
                )
                Spacer(Modifier.height(10.dp))
            }
            Text("Son ${snapshot.recent.size} karşılaştırılabilir denemenin ${snapshot.latestTaggedExams} tanesinde konu etiketi var. Etiketsiz yanlışlara konu uydurulmuyor.",
                color = Grey,fontSize = 11.sp,lineHeight = 16.sp,
                modifier = Modifier.padding(top = 3.dp))
        }
        Spacer(Modifier.height(23.dp))

        DashboardCard {
            Text("Gelişimi doğrulamanın yolu",color = Black,
                fontSize = 16.sp,fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("1. En üstteki tekrar eden konuyu seç.\n2. Kısa tekrar ve hedefli soru çözümü yap.\n3. Yeni denemede aynı konuyu yeniden etiketle ve gerçek değişimi gör.",
                color = Grey,fontSize = 12.sp,lineHeight = 20.sp)
            Spacer(Modifier.height(13.dp))
            ThinAction("Yeni denemeyle kontrol et",onAdd)
        }
    }
}

@Composable
private fun AnalysisPill(label:String,selected:Boolean,modifier:Modifier,onClick:()->Unit) {
    Surface(modifier=modifier,onClick=onClick,shape=CircleShape,
        color=if(selected) Black else Pale) {
        Text(label,Modifier.padding(horizontal=13.dp,vertical=10.dp),
            fontSize=12.sp,fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Medium,
            color=if(selected) White else Grey,textAlign=TextAlign.Center,maxLines=1)
    }
}

@Composable
private fun DashboardCard(content:@Composable ColumnScope.()->Unit) {
    Surface(color=White,shape=RoundedCornerShape(24.dp),
        border=BorderStroke(1.dp,Border),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(18.dp),content=content)
    }
}

@Composable
private fun SectionHeader(title:String,subtitle:String) {
    Text(title,color=Black,fontSize=19.sp,fontWeight=FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(subtitle,color=Grey,fontSize=12.sp,lineHeight=17.sp)
    Spacer(Modifier.height(13.dp))
}

@Composable
private fun MetricLabel(label:String,value:String,modifier:Modifier=Modifier) {
    Column(modifier) {
        Text(label,color=Grey,fontSize=11.sp)
        Spacer(Modifier.height(4.dp))
        Text(value,color=Black,fontWeight=FontWeight.Bold,fontSize=16.sp,
            maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}

@Composable
private fun SpeedGauge(net:Double, limit:Int, reduceMotion:Boolean) {
    val maxValue = limit.coerceAtLeast(1)
    val progress = (net / maxValue).toFloat().coerceIn(0f,1f)
    var revealed by remember(net,maxValue) { mutableFloatStateOf(0f) }
    LaunchedEffect(net,maxValue) { revealed=1f }
    val animated by animateFloatAsState(progress*revealed,
        animationSpec=tween(if(reduceMotion) 0 else 880),label="NetPusula")
    Box(Modifier.fillMaxWidth().height(209.dp),contentAlignment=Alignment.Center) {
        Canvas(Modifier.size(208.dp).semantics {
            contentDescription = "${LocalData.net(net)} net, $maxValue soruda"
        }) {
            val sw=15.dp.toPx()
            val inset=sw/2 + 7.dp.toPx()
            val rectSize=Size(size.width-inset*2,size.height-inset*2)
            val centerOffset=Offset(inset,inset)
            drawArc(Pale,startAngle=150f,sweepAngle=240f,useCenter=false,
                topLeft=centerOffset,size=rectSize,style=Stroke(sw,cap=StrokeCap.Round))
            drawArc(Black,startAngle=150f,sweepAngle=240f*animated,useCenter=false,
                topLeft=centerOffset,size=rectSize,style=Stroke(sw,cap=StrokeCap.Round))
            val radius=(size.width/2)-34.dp.toPx()
            for(i in 0..12) {
                val rad=Math.toRadians((150.0+i*20.0))
                val c=Offset(size.width/2f,size.height/2f)
                val r1=radius-5.dp.toPx()
                val r2=radius+0.dp.toPx()
                drawLine(if(i/12f<=animated) Black else Off,
                    Offset(c.x+cos(rad).toFloat()*r1,c.y+sin(rad).toFloat()*r1),
                    Offset(c.x+cos(rad).toFloat()*r2,c.y+sin(rad).toFloat()*r2),
                    strokeWidth=1.35.dp.toPx())
            }
        }
        Column(horizontalAlignment=Alignment.CenterHorizontally,
            modifier=Modifier.padding(top=24.dp)) {
            Text(LocalData.net(net),fontSize=37.sp,fontWeight=FontWeight.Bold,color=Black)
            Spacer(Modifier.height(3.dp))
            Text("SON DENEME NETİ",fontSize=10.sp,fontWeight=FontWeight.Medium,
                letterSpacing=1.0.sp,color=Grey)
            Spacer(Modifier.height(7.dp))
            Text("$maxValue soru",fontSize=11.sp,color=Grey)
        }
    }
}

@Composable
private fun DistributionDonut(correct:Int, wrong:Int, blank:Int, reduceMotion:Boolean) {
    val total=max(1,correct+wrong+blank)
    var revealed by remember(correct,wrong,blank) { mutableFloatStateOf(0f) }
    LaunchedEffect(correct,wrong,blank) { revealed=1f }
    val completion by animateFloatAsState(revealed,
        animationSpec=tween(if(reduceMotion) 0 else 540),label="Distribution")
    Box(Modifier.size(79.dp),contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(7.dp).semantics {
            contentDescription = "$correct doğru, $wrong yanlış, $blank boş"
        }) {
            val sw=8.dp.toPx()
            var start=-90f
            listOf(correct to Black, wrong to Grey, blank to Off).forEach { (v,c) ->
                val sweep=360f*v/total*completion
                if(sweep>0f)drawArc(c,start,sweep,useCenter=false,
                    style=Stroke(sw,cap=StrokeCap.Butt))
                start+=sweep
            }
        }
        Text("$correct",fontWeight=FontWeight.Bold,fontSize=16.sp,color=Black)
    }
}

@Composable
private fun ModernTrendChart(exams:List<ExamRecord>,reduceMotion:Boolean) {
    if(exams.size<2) {
        Box(Modifier.fillMaxWidth().height(140.dp),contentAlignment=Alignment.Center) {
            Text("Grafik için ikinci denemeni ekle",color=Grey,fontSize=12.sp)
        }
        return
    }
    val v=exams.map{it.net.toFloat()}
    val lo=(v.minOrNull() ?: 0f)-3f
    val hi=(v.maxOrNull() ?: 1f)+3f
    var revealed by remember(exams.map { it.id }) { mutableFloatStateOf(0f) }
    LaunchedEffect(exams.map { it.id }) { revealed=1f }
    val fraction by animateFloatAsState(revealed,
        animationSpec=tween(if(reduceMotion) 0 else 700),label="NetTrendReveal")
    Canvas(Modifier.fillMaxWidth().height(145.dp).semantics{
        contentDescription = "Net gelişimi: "+exams.joinToString(", "){LocalData.net(it.net)}
    }) {
        val left=9.dp.toPx()
        val right=size.width-9.dp.toPx()
        val top=11.dp.toPx()
        val bottom=size.height-13.dp.toPx()
        repeat(3) { idx ->
            val y=top+(bottom-top)*(idx+1)/4
            drawLine(Border,Offset(left,y),Offset(right,y),strokeWidth=1.dp.toPx())
        }
        val plot=Path()
        v.forEachIndexed { index,value ->
            val x=left+(right-left)*index/(v.size-1).toFloat()
            val y=bottom-(bottom-top)*(value-lo)/(hi-lo).coerceAtLeast(1f)
            if(index==0)plot.moveTo(x,y) else plot.lineTo(x,y)
        }
        drawPath(plot,Black,style=Stroke(width=2.5.dp.toPx(),cap=StrokeCap.Round),
            alpha=fraction)
        v.forEachIndexed { index,value ->
            val x=left+(right-left)*index/(v.size-1).toFloat()
            val y=bottom-(bottom-top)*(value-lo)/(hi-lo).coerceAtLeast(1f)
            drawCircle(Black,2.7.dp.toPx(),Offset(x,y),alpha=fraction)
        }
    }
    Spacer(Modifier.height(3.dp))
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(LocalData.date(exams.first().examDate),color=Grey,fontSize=10.sp)
        Text(LocalData.date(exams.last().examDate),color=Grey,fontSize=10.sp)
    }
}

@Composable
private fun SubjectLossLine(subject:SubjectInsight,reduceMotion:Boolean) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        TinySubjectRing(subject.fraction,reduceMotion)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(subject.name,color=Black,fontWeight=FontWeight.SemiBold,fontSize=13.sp,
                maxLines=1)
            Spacer(Modifier.height(4.dp))
            Text("${subject.correct} D · ${subject.wrong} Y · ${subject.blank} B",
                color=Grey,fontSize=11.sp)
            Spacer(Modifier.height(4.dp))
            Text("Tüm kayıp sorular doğru olursa en fazla +${LocalData.net(subject.theoreticalRoom)} net",
                color=Grey,fontSize=10.sp,lineHeight=13.sp)
        }
        Column(horizontalAlignment=Alignment.End) {
            Text(LocalData.net(subject.net),fontSize=17.sp,
                fontWeight=FontWeight.Bold,color=Black)
            Text("/ ${subject.questions}",color=Grey,fontSize=10.sp)
            subject.difference?.let {
                Spacer(Modifier.height(4.dp))
                Text(signedNet(it),fontSize=10.sp,
                    color=Black,fontWeight=FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TinySubjectRing(progress:Float,reduceMotion:Boolean) {
    var revealed by remember(progress) { mutableFloatStateOf(0f) }
    LaunchedEffect(progress) { revealed=1f }
    val value by animateFloatAsState(progress*revealed,
        animationSpec=tween(if(reduceMotion)0 else 500),label="SubjectGauge")
    Canvas(Modifier.size(57.dp).padding(5.dp)) {
        val sw=5.5.dp.toPx()
        drawArc(Pale,-90f,360f,false,style=Stroke(sw,cap=StrokeCap.Round))
        if(value>0f)drawArc(Black,-90f,value*360f,false,style=Stroke(sw,cap=StrokeCap.Round))
    }
}

@Composable
private fun FocusPriorityCard(
    index:Int,
    topic:TopicPriority,
    expand:Boolean,
    completion:FocusCompletion?,
    snapshot:AnalysisSnapshot,
    onExpand:()->Unit,
    onComplete:()->Unit
) {
    DashboardCard {
        Row(verticalAlignment=Alignment.Top) {
            Surface(shape=RoundedCornerShape(12.dp),color=Pale) {
                Text("${index+1}",Modifier.padding(horizontal=12.dp,vertical=8.dp),
                    fontSize=14.sp,fontWeight=FontWeight.Bold,color=Black)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(topic.lessonName,color=Grey,fontSize=11.sp)
                Spacer(Modifier.height(4.dp))
                Text(topic.label,color=Black,fontWeight=FontWeight.SemiBold,
                    fontSize=15.sp,lineHeight=20.sp)
                Spacer(Modifier.height(7.dp))
                Text("${topic.recordedExams} denemede · ${topic.wrong} yanlış · ${topic.blank} boş",
                    fontSize=11.sp,color=Grey)
                if(topic.commonReason!="Belirtilmedi") {
                    Spacer(Modifier.height(5.dp))
                    Text("Kaydedilen neden: ${topic.commonReason}",fontSize=11.sp,color=Grey)
                }
            }
        }
        Spacer(Modifier.height(13.dp))
        Surface(onClick=onExpand,shape=RoundedCornerShape(15.dp),color=Pale) {
            Row(Modifier.fillMaxWidth().padding(horizontal=13.dp,vertical=12.dp),
                verticalAlignment=Alignment.CenterVertically) {
                Text(if(completion!=null) "Çalışma kaydını gör" else "Eksik kapatma adımları",
                    fontWeight=FontWeight.SemiBold,fontSize=12.sp,color=Black,
                    modifier=Modifier.weight(1f))
                Icon(Icons.Rounded.ChevronRight,null,tint=Black,modifier=Modifier.size(18.dp))
            }
        }
        AnimatedVisibility(expand) {
            Column(Modifier.fillMaxWidth().padding(top=12.dp)) {
                FocusJournal.plan(topic.commonReason).forEachIndexed { i,step ->
                    Row(Modifier.fillMaxWidth().padding(bottom=10.dp)) {
                        Text("${i+1}.",fontWeight=FontWeight.Bold,color=Black,fontSize=12.sp)
                        Spacer(Modifier.width(7.dp))
                        Text(step,color=Grey,fontSize=12.sp,lineHeight=17.sp)
                    }
                }
                if(completion==null) {
                    DarkAction("Çalışmayı tamamladım",onComplete)
                    Spacer(Modifier.height(7.dp))
                    Text("Bu işlem yalnızca kendi beyanını kaydeder; net artışı iddiası oluşturmaz.",
                        color=Grey,fontSize=10.sp,lineHeight=14.sp)
                } else {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Check,null,tint=Black,modifier=Modifier.size(16.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Çalışma kaydı · ${LocalData.date(completion.completedAt)}",
                            color=Black,fontSize=12.sp,fontWeight=FontWeight.Medium)
                    }
                    val later=FocusJournal.nextExamAfter(completion,snapshot)
                    Spacer(Modifier.height(8.dp))
                    Text(if(later==null) "Bu çalışmadan sonraki karşılaştırılabilir deneme henüz yok."
                         else "Sonraki denemede toplam net değişimi: ${signedNet(later.net-completion.baselineExamNet)} net. Konu etkisini tek başına kanıtlamaz.",
                        fontSize=11.sp,color=Grey,lineHeight=16.sp)
                    Spacer(Modifier.height(8.dp))
                    ThinAction("Yeniden çalıştım",onComplete)
                }
            }
        }
    }
}

@Composable
private fun DarkAction(text:String,onClick:()->Unit) {
    Surface(onClick=onClick,color=Black,shape=RoundedCornerShape(15.dp),
        modifier=Modifier.fillMaxWidth()) {
        Text(text,Modifier.padding(vertical=13.dp,horizontal=10.dp),
            textAlign=TextAlign.Center,color=White,fontWeight=FontWeight.SemiBold,fontSize=13.sp)
    }
}

@Composable
private fun ThinAction(text:String,onClick:()->Unit) {
    Surface(onClick=onClick,color=Pale,shape=RoundedCornerShape(14.dp),
        modifier=Modifier.fillMaxWidth()) {
        Text(text,Modifier.padding(vertical=12.dp,horizontal=10.dp),
            textAlign=TextAlign.Center,color=Black,fontWeight=FontWeight.SemiBold,fontSize=12.sp)
    }
}

private fun signedNet(value:Double):String =
    (if(value>0) "+" else "") + LocalData.net(value)
