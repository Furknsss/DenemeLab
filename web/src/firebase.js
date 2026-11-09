import { initializeApp } from "firebase/app";
import {
  getAuth, GoogleAuthProvider, signInWithPopup,
  signInWithEmailAndPassword, createUserWithEmailAndPassword,
  sendPasswordResetEmail, updateProfile, signOut, onAuthStateChanged
} from "firebase/auth";
import { getFirestore, doc, getDocFromServer, onSnapshot } from "firebase/firestore";
import { unzipSync, strFromU8 } from "fflate";

const projectId = import.meta.env.VITE_FIREBASE_PROJECT_ID || "denemelab-yks-2026";
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || projectId + ".firebaseapp.com",
  projectId,
};
export const configured = Boolean(firebaseConfig.apiKey && firebaseConfig.projectId);
const app = configured ? initializeApp(firebaseConfig) : null;
export const auth = app ? getAuth(app) : null;
export const db = app ? getFirestore(app) : null;
const provider = new GoogleAuthProvider();
provider.setCustomParameters({ prompt: "select_account" });

export const authActions = {
  listen: (next) => auth ? onAuthStateChanged(auth, next) : () => {},
  google: () => signInWithPopup(auth, provider),
  email: (email, password) => signInWithEmailAndPassword(auth, email, password),
  register: async (email, password, name) => {
    const result = await createUserWithEmailAndPassword(auth, email, password);
    if (name.trim()) await updateProfile(result.user, { displayName: name.trim() });
    return result;
  },
  reset: (email) => sendPasswordResetEmail(auth, email.trim()),
  signOut: () => signOut(auth),
};

const MAX_ZIP = 8 * 1024 * 1024;
function decodeBase64(value) {
  if (typeof value !== "string" || value.length > 230000) throw new Error("Bulut yedeği bölümü geçersiz.");
  const raw = atob(value);
  return Uint8Array.from(raw, c => c.charCodeAt(0));
}
function hex(bytes) {
  return [...new Uint8Array(bytes)].map(x => x.toString(16).padStart(2, "0")).join("");
}
function pref(record, key, fallback) {
  const field = record?.preferences?.[key];
  if (!field || !("value" in field)) return fallback;
  return field.value;
}
function asArray(text) {
  try {
    const x = JSON.parse(typeof text === "string" ? text : "[]");
    return Array.isArray(x) ? x : [];
  } catch { throw new Error("Bulut yedeğinin kayıt listesi bozuk."); }
}
function assertSaneExam(v) {
  return Boolean(
    v && typeof v === "object" &&
    typeof v.name === "string" &&
    ["Genel", "Branş"].includes(v.category) &&
    Number.isFinite(v.correct) && Number.isFinite(v.wrong) &&
    Number.isFinite(v.blank) && v.correct >= 0 && v.wrong >= 0 && v.blank >= 0
  );
}
function parseBackup(bytes, meta) {
  let data;
  try {
    const files = unzipSync(bytes);
    if (!files["data.json"] || files["data.json"].length > 16 * 1024 * 1024)
      throw new Error("Yedeğin veri dosyası bulunamadı.");
    data = JSON.parse(strFromU8(files["data.json"]));
  } catch (e) {
    throw new Error("Bulut yedeği arşivi okunamadı: " + e.message);
  }
  if (data?.version !== 1 || !data?.preferences) throw new Error("Desteklenmeyen DenemeLab yedeği.");
  const exams = asArray(pref(data, "exams", "[]")).filter(assertSaneExam)
    .sort((a,b) => Number(b.examDate || b.createdAt || 0) - Number(a.examDate || a.createdAt || 0));
  const questions = asArray(pref(data, "questions", "[]"));
  if (exams.length !== Number(meta.exams) || questions.length !== Number(meta.questions))
    throw new Error("Bulut yedeğinde kayıt sayıları uyuşmuyor. Android kayıtlarını değiştirmedik.");
  const target = Object.fromEntries(
    ["university", "department", "targetRank", "schoolGrade", "programCode",
      "universityType", "language", "scholarship", "scoreType"]
      .map(k => [k, pref(data, k, "")])
  );
  return {
    exams, questions, target,
    profile: {
      firstName: String(pref(data, "studentFirstName", "") || ""),
      surname: String(pref(data, "studentSurname", "") || ""),
      grade: String(pref(data, "studentGrade", "") || "")
    },
    publisherCatalog: asArray(pref(data,"publisher_catalog_v1","[]")),
    mood: String(pref(data, "examMood", "") || ""),
    createdAt: Number(data.createdAt) || 0
  };
}
async function getValidatedBackup(uid, id) {
  if (!/^[a-f0-9-]{20,64}$/i.test(id)) throw new Error("Geçersiz bulut yedeği kimliği.");
  const root = ["users", uid, "backups", id];
  const metaSnap = await getDocFromServer(doc(db, ...root));
  if (!metaSnap.exists()) throw new Error("Bulut yedeği bilgileri bulunamadı.");
  const meta = metaSnap.data();
  const count = Number(meta.partCount);
  const total = Number(meta.byteSize);
  if (!Number.isInteger(count) || count < 1 || count > 64 || total <= 0 || total > MAX_ZIP)
    throw new Error("Bulut yedeğinin boyutu geçersiz.");
  const byteParts = [];
  for (let start = 0; start < count; start += 8) {
    const chunk = await Promise.all(Array.from({length: Math.min(8,count-start)}, async (_,offset) => {
      const i = start + offset;
      const snap = await getDocFromServer(doc(db, ...root, "parts", String(i).padStart(3, "0")));
      if (!snap.exists() || Number(snap.get("index")) !== i)
        throw new Error("Bulut yedeğinin bir bölümü eksik.");
      const bytes = decodeBase64(snap.get("base64"));
      if (bytes.length > 160 * 1024) throw new Error("Yedek bölümü beklenenden büyük.");
      return bytes;
    }));
    byteParts.push(...chunk);
  }
  const combined = new Uint8Array(byteParts.reduce((n,p) => n + p.length, 0));
  let cursor = 0;
  byteParts.forEach(part => { combined.set(part,cursor); cursor += part.length; });
  if (combined.length !== total) throw new Error("Bulut yedeği boyutu uyuşmuyor.");
  const digest = await crypto.subtle.digest("SHA-256", combined);
  if (hex(digest) !== meta.sha256) throw new Error("Bulut yedeği SHA-256 doğrulamasını geçemedi.");
  return { ...parseBackup(combined, meta), backupId: id,
    uploadedAt: Number(meta.createdAt) || 0 };
}

/** Strictly READ-ONLY. Android retains ownership of cloud backups and restore. */
export function subscribeBackup(uid, onUpdate) {
  let request = 0;
  const ref = doc(db, "users", uid, "metadata", "current");
  return onSnapshot(ref, async (snapshot) => {
    if (snapshot.metadata.fromCache) {
      onUpdate({ phase: "loading", message: "Sunucuyla doğrulanıyor…" });
      return;
    }
    const myRequest = ++request;
    const id = snapshot.get("id");
    if (!id) {
      onUpdate({ phase: "empty", message: "Bu hesapta henüz bir Android bulut yedeği yok." });
      return;
    }
    onUpdate({ phase: "loading", message: "Android bulut yedeği doğrulanıyor…" });
    try {
      const data = await getValidatedBackup(uid,id);
      if (request === myRequest) onUpdate({phase:"ready",data, backupId:id,
        uploadedAt: Number(snapshot.get("uploadedAt")) || data.uploadedAt});
    } catch(e) {
      if (request === myRequest) onUpdate({phase:"error",message:e.message});
    }
  }, (e) => {
    request++;
    onUpdate({phase:"error",message:e.code === "permission-denied" ?
      "Firebase hesabının veri okuma izni yok. Firestore kurallarını kontrol et." : e.message});
  });
}
