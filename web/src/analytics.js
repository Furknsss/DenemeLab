const TR = "tr-TR";
export const net = (x) => (Number(x?.correct) || 0) - (Number(x?.wrong) || 0) / 4;
export const fnet = x => Number(x ?? 0).toLocaleString(TR, {minimumFractionDigits:2,maximumFractionDigits:2});
export const fdate = x => Number(x) > 0 ?
  new Date(Number(x)).toLocaleDateString(TR,{day:"numeric",month:"short",year:"numeric"}) : "Tarih yok";
export const stamp = x => Number(x?.examDate || x?.createdAt || 0);
export const sortExams = exams => [...exams].sort((a,b) =>
  stamp(b)-stamp(a) || Number(b.createdAt||0)-Number(a.createdAt||0));
export const examKind = x => x?.category === "Branş" ? "Branş · " + (x.subject || "Diğer") :
  x?.examType === "AYT" ? "AYT · " + (x.track || "Genel") : "TYT";
export const scopeKey = x => x?.category === "Branş" ?
  "Branş|" + x.examType + "|" + x.subject : x?.examType === "AYT" ?
    "Genel|AYT|" + (x.track || "Genel") : "Genel|TYT";
export function getScopes(exams) {
  const keys = new Map();
  sortExams(exams).forEach(x => {
    const key = scopeKey(x);
    if (!keys.has(key)) keys.set(key,{key,label:examKind(x)});
  });
  return [...keys.values()];
}
export const scoped = (exams,key) =>
  sortExams(exams).filter(x => scopeKey(x) === key);
export function getTrend(rows) {
  const sorted = [...rows].sort((a,b)=>stamp(a)-stamp(b));
  return sorted.slice(-12).map((x,i)=>({
    id: String(x.id||i),name:x.name||"Deneme",date:fdate(stamp(x)),
    net:Number(net(x).toFixed(2)), index:i+1
  }));
}
const normalize = x => String(x||"").toLocaleLowerCase(TR)
  .normalize("NFD").replace(/[\u0300-\u036f]/g,"").replace(/ı/g,"i")
  .replace(/[^a-z0-9]+/g," ").trim();
const keyOf = x => String(x.lessonKey) + "|" + normalize(x.topic);
function evidence(exam) {
  const map = new Map();
  for (const x of exam?.topicOutcomes || []) {
    if (!x?.topic || !Number.isFinite(x.correct) || !Number.isFinite(x.wrong) ||
      !Number.isFinite(x.blank) || x.correct < 0 || x.wrong < 0 || x.blank < 0) continue;
    map.set(keyOf(x),{topic:x.topic,lesson:x.lessonName||"Ders",
      lessonKey:x.lessonKey,correct:x.correct,wrong:x.wrong,blank:x.blank,verified:true});
  }
  for (const x of exam?.topicIssues || []) {
    if (!x?.topic || !Number.isFinite(x.count) || x.count<=0 ||
      !["Yanlış","Boş"].includes(x.kind) || map.has(keyOf(x))) continue;
    const key=keyOf(x);
    const previous=map.get(key)||{topic:x.topic,lesson:x.lessonName||"Ders",
      lessonKey:x.lessonKey,correct:0,wrong:0,blank:0,verified:false};
    previous[x.kind==="Yanlış"?"wrong":"blank"]+=x.count;
    map.set(key,previous);
  }
  return map;
}
export function getTopicStats(rows) {
  const lastFive=sortExams(rows).slice(0,5);
  const aggregate=new Map();
  lastFive.forEach(exam=>{
    evidence(exam).forEach((x,key)=>{
      const current=aggregate.get(key)||{...x,correct:0,wrong:0,blank:0,exams:0};
      current.correct+=x.correct;current.wrong+=x.wrong;current.blank+=x.blank;current.exams++;
      aggregate.set(key,current);
    });
  });
  const all=[...aggregate.entries()].map(([key,x])=>({
    ...x,key, loss:x.wrong*1.25+x.blank,
    gain:x.correct-x.wrong/4
  }));
  const losses=all.filter(x=>x.loss>0).sort((a,b)=>b.loss-a.loss||b.exams-a.exams);
  const gains=all.filter(x=>x.correct>0 && x.gain>0).sort((a,b)=>b.gain-a.gain);
  const repeated=[];
  const latest=lastFive[0];
  if(latest) {
    evidence(latest).forEach((x,key)=>{
      if(x.wrong<=0) return;
      let count=0,wrong=0,blank=0;
      for(const exam of lastFive) {
        const row=evidence(exam).get(key);
        if(!row||row.wrong<=0) break;
        count++;wrong+=row.wrong;blank+=row.blank;
      }
      if(count>=2) repeated.push({...x,key,count,wrong,blank,loss:wrong*1.25+blank});
    });
  }
  repeated.sort((a,b)=>b.count-a.count||b.loss-a.loss);
  return {losses,gains,repeated,coverage:lastFive.filter(x=>(x.topicOutcomes||[]).length>0).length,
    examCount:lastFive.length};
}
export function getLessons(rows) {
  const byKey=new Map();
  for(const exam of sortExams(rows)) for(const part of (exam.parts||[])) {
    if(!byKey.has(part.key)){
      byKey.set(part.key,{...part,examName:exam.name,examDate:stamp(exam),
        net:net(part), blank:part.questions-part.correct-part.wrong});
    }
  }
  return [...byKey.values()];
}
export function getMood(rows) {
  const levels=["Sakin","Normal","Heyecanlı","Telaşlı","Stresli"];
  const past=sortExams(rows).slice(0,5).filter(x=>levels.includes(x.mood) || x.mood==="Çok stresli");
  if(!past.length) return null;
  const value=past.reduce((sum,x)=>sum+Math.max(0,levels.indexOf(x.mood==="Çok stresli"?"Stresli":x.mood)),0)/past.length;
  return {value,label:levels[Math.min(4,Math.round(value))],count:past.length,
    latest:past[0].mood,exam:past[0].name};
}
export function getSummary(rows) {
  const latest=sortExams(rows)[0];
  const recent=sortExams(rows).slice(0,3);
  const average=recent.length?recent.reduce((n,x)=>n+net(x),0)/recent.length:null;
  const previous=sortExams(rows)[1];
  return {latest,average,best:rows.length?Math.max(...rows.map(net)):null,
    delta:latest&&previous?net(latest)-net(previous):null};
}
