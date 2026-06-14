# BACKLOG — PushIT_app (client KMP)

Backlog issu de l'audit complet du 2026-06-12 (sécurité, réseau/API, archi/UI, code
plateforme Android/iOS, build/tests/hygiène). Sévérités : **P0** bloquant · **P1** important ·
**P2/P3** à nettoyer. Tout le travail coché est commité et poussé sur **`Foxugly/PushIT_app`**
(public, branche `main`, CI verte).

---

## ✅ Fait le 2026-06-12

- [x] **Gestion d'erreurs API** (WIP repris) — `decodeBody` + `ResponseDecodingException`
  (réponse non-JSON inattendue) + `formatApiErrorBody` (messages DRF lisibles), branché à l'UI.
- [x] **Tests host activés sur Windows** — `withHostTestBuilder {}` dans `composeApp/build.gradle.kts`
  → `:composeApp:testAndroidHostTest` (les `commonTest` ne tournaient que sur iOS natif avant).
- [x] **(a) Secret retiré** — `git rm --cached androidApp/google-services.json` + `.gitignore`
  (`**/google-services.json`, `**/GoogleService-Info.plist`, `*.bak`, `emulator-screen.png`).
- [x] **(b) Contrat API resynchronisé** — `composeApp/schema.yaml` recopié du serveur ; `username`
  retiré de `UserProfile`/`RegisterRequest`/`RegisterScreen`/`SettingsScreen`/`AuthRepository` ;
  `turnstile_token` ajouté (optionnel) ; `Json { explicitNulls = false }` ; 2 tests de non-régression.
- [x] **(c) Rejeu 401 corrigé** — `refreshIfNeeded()` (refresh seul) + `apiCall` rejoue le `block()`
  d'origine (verbe+corps préservés) ; garde anti-double-refresh concurrent.
- [x] **(d) UI** — bouton Retry du détail fonctionnel (`reloadKey`) ; dédup pagination (`distinctBy { id }`).

### Lot P1 + P2/P3 (2026-06-12, suite)

- [x] **P1 POST_NOTIFICATIONS runtime** — demandé dans `MainActivity` sur Android 13+.
- [x] **P1 baseUrl par build-type** — `MainActivity` choisit dev (`10.0.2.2`) vs prod (`pushit-api.foxugly.com`)
  selon `BuildConfig.DEBUG` (+ `enableHttpLogging`), passé à `App()` → `PushItApi`. Logging Ktor `NONE` en release.
- [x] **P1 fuite observer FCM** — `DisposableEffect` + `FcmTokenProvider.stopObservingTokenChanges()` (expect/actual).
- [x] **P1 double-nav QrScanner** — navigation déplacée après le link, dans la coroutine.
- [x] **P1 back stack** — vraie pile `mutableStateListOf<Screen>()` (`navigateTo`/`navigateBack`/`resetTo`).
- [x] **P1 garde concurrence pagination** — `refresh()` unique gardé par `isRefreshing`.
- [x] **P1 material-icons via catalog** — `libs.compose.material.icons.core` avec sa propre version épinglée (1.7.3).
- [x] **P1 tests** — interface `TokenStore` + adaptateur → `PushItApi` accepte un `MockEngine` ; 2 tests
  `PushItApiAuthTest` valident le rejeu 401 (POST+corps préservés, token frais) et le `onAuthFailure`/clear.
  `isReturnDefaultValues=true` pour `android.util.Log`. (Repos encore non couverts — voir P1 restant.)
- [x] **P1 CI** — `.github/workflows/ci.yml` (test host + APK debug si secret Firebase). *(Remote/push : voir P1 restant.)*
- [x] **P2 HttpClient `Closeable`** + **204/corps vide** géré dans `decodeBody` + **`NetworkException`** (timeout/offline) distinct d'`ApiException`.
- [x] **P2 NotificationChannel créé une seule fois** + **broadcast `setPackage`** (durcissement) + **`@Volatile`** sur le token FCM (Android).
- [x] **P2 `runtimeError` dismissible** (tap) + **`ErrorBanner`** homogène (Settings, QrScanner) + **`handleToken` mort** supprimé.
- [x] **P2 `allowBackup=false`** + **`googleServices` `version.ref`** + **docs CLAUDE/README** corrigées (modules, commandes, Kotlin 2.3.10, google-services).

### Décisions tranchées + implémentées (2026-06-12)

- [x] **Turnstile → mobile login-only** — l'inscription est retirée du mobile (`RegisterScreen` supprimé,
  `Screen.Register` retiré, hint « créer un compte sur pushit.foxugly.com » sur le login). Plus de
  blocage captcha ; le login n'a pas de Turnstile. *(La couche data `register()`/`RegisterRequest` reste, inutilisée.)*
- [x] **App-token : garder au logout + bouton « Unlink this device »** dans Settings (efface l'app-token local).

---

## ✅ Inbox destinataire + push (2026-06-14)

- [x] **Logo par app dans les dossiers** (decode bytes → `ImageBitmap`, placeholder 1re lettre).
- [x] **Dossiers pour apps liées sans notif** (#4) + **« tout marquer comme lu » par dossier** (#3).
- [x] **Deep-link push → message** (#1) — `data.notification_id` → PendingIntent → MainActivity → route.
- [x] **Badge OS du nombre de non-lues** (#2) — iOS natif ; Android best-effort (notif IMPORTANCE_MIN).
- [x] **Fenêtre récente + « charger plus ancien »** — `sent_since` (30 j par défaut, recharge tout).
- [x] **Login : erreur conviviale** — plus de message API brut (`ApiException` → identifiants / serveur).

## À faire (revue 2026-06-14)

- [ ] **P3 — Utilitaires de date** : `formatTimestamp`/`formatDetailTimestamp` font du `split("T")`
  manuel, et `isoUtcDaysAgo` est en `SimpleDateFormat`/`NSDate`. Envisager **kotlinx-datetime**
  pour unifier les 3 et ouvrir le « il y a 2 h » relatif.
- [ ] **P3 — « Charger plus ancien » progressif** : aujourd'hui un seul appui recharge tout
  l'historique ; pourrait être incrémental (par fenêtres) si le volume grossit.

> Les constats de l'audit multi-agents (2026-06-14) seront ajoutés ici après vérification.

## 🔴 P0 — Bloquants restants

- [ ] **iOS — push totalement cassé** *(Renaud : iOS pas encore géré)*
  - `iosApp/iosApp/iOSApp.swift:34-38` : `didReceiveRegistrationToken` est un **stub vide** → le
    token FCM n'arrive jamais au Kotlin `FcmTokenProvider` → aucun device iOS enregistré. Bridger le
    token (exposer `FcmTokenProvider.instance.updateToken(token)` depuis le framework) + récupérer le
    token initial au démarrage (équivalent du `refreshToken()` Android, manquant côté iOS).
  - `iosApp/iosApp/Info.plist` : manque `GoogleService-Info.plist` (→ `FirebaseApp.configure()` **crash**
    au lancement), `UIBackgroundModes = remote-notification`, entitlement `aps-environment` (APNs),
    et `NSCameraUsageDescription` (dès l'implémentation réelle du scanner QR).
- [~] **Release Android — durcissement (config faite, validée au build)** :
  - [x] **R8 activé** (`isMinifyEnabled = true`) + `proguard-rules.pro` (keep-rules kotlinx.serialization
    pour les DTOs `data.api.**`, Ktor, service FCM). `:androidApp:assembleRelease` **passe** (R8 OK,
    `mapping.txt` généré, APK minifié produit).
  - [x] **`signingConfig` release conditionnel** — lit `keystore.properties` (racine, git-ignoré) ou env ;
    absent → release non signé mais minifié (validable). `*.jks`/`keystore.properties` git-ignorés. Doc README.
  - [x] **Smoke-test runtime du build minifié** (émulateur Pixel_7, 2026-06-13) : APK release minifié
    (debug-signé pour le test) installé + lancé → démarre sans crash, FCM token récupéré, permission
    POST_NOTIFICATIONS demandée, écran login rendu. R8 + keep-rules validés à l'exécution.
  - [ ] **Reste (action Renaud)** : créer le keystore release + `keystore.properties` (cf. README), puis
    ajouter son **SHA-1** à la restriction de clé Firebase.

---

## 🟠 P1 — Restants (décision / infra)

- [x] ~~**Turnstile vs mobile**~~ → **tranché : mobile login-only** (voir ci-dessus).
- [x] ~~**Remote + push**~~ → **fait** : poussé sur **`Foxugly/PushIT_app`** (public, `main`), historique
  scrubé de `google-services.json` avant push, CI vert. *(NB : `Foxugly/PushIT_frontend` est le frontend
  **web Angular** de PushIT — projet distinct, pas l'app mobile.)*
- [ ] **CI build APK** *(action Renaud, optionnel)* — pour activer le job `build-debug-apk`, ajouter le secret
  `GOOGLE_SERVICES_JSON_B64` + la variable repo `HAS_FIREBASE_SECRET=true`.
- [x] ~~**Couverture de tests (suite)**~~ → **fait** : `AuthRepository` (7), `NotificationRepository` (3),
  `DeviceLinkManager` (4) testés via `MockEngine` + fakes (`TokenStore` + nouveau seam `FcmTokenSource`).
  27 tests host au total. Reste possible plus tard : tests Compose UI (login/nav) — nécessitent Robolectric/émulateur.
- [x] ~~**Unlink côté serveur**~~ → **fait** : nouvel endpoint backend `POST /devices/unlink/`
  (`PushIT_server`, désactive le `DeviceApplicationLink`, idempotent, 3 tests) + le bouton « Unlink this
  device » appelle `deviceLinkManager.unlinkCurrentDevice()` (serveur puis app-token local, garde le local
  si le serveur échoue). 3 tests mobile. *(Le frontend web n'est pas concerné : endpoint device-side app-token,
  la console web gère les devices par `device_id` — feature distincte si besoin un jour.)*

---

## 🟡 P2 / P3 — Restants (différés, avec raison)

**iOS** *(plateforme gérée séparément — non compilable sous Windows)*
- [ ] Tokens iOS en `NSUserDefaults` non chiffré → **Keychain**.
- [ ] `@Volatile`/synchro sur `currentToken` iOS (fait côté Android).
- [ ] Scanner QR iOS = stub qui spamme `onError` → message neutre.
- [ ] Pas de cible de test iOS (`commonTest` ne tourne que sur androidHostTest).

**Lourds / à valider séparément**
- [x] ~~**i18n totalement absente**~~ → **fait (2026-06-14)** : catalogue `Strings` FR/NL/EN (`ui/i18n/`, approche map façon web AppCopyService, pas de compose.resources) + `LocalStrings` + sélecteur FR/NL/EN dans Settings (persisté dans TokenStorage). Tous les écrans + erreurs réseau (NetworkException) localisés. Brand (PushIT/Foxugly) inchangé.
- [ ] `material3 = 1.10.0-alpha05` → stable : *la version stable cible n'est pas évidente (les material3 JetBrains
  sont versionnés à part de Compose 1.10.3) ; bump à valider par un build complet, pas fait à l'aveugle.*
- [ ] `Notification.status` en `enum` : *gardé en `String` volontairement — un `enum` kotlinx planterait si le
  serveur ajoute une valeur (`StatusEnum`) ; nécessiterait un fallback. À faire avec soin.* (Fixture test `"delivered"` à corriger en passant.)
- [ ] Parsing timestamp `split("T")` → `kotlinx-datetime` (fonctionne aujourd'hui ; polish).
- [ ] `isAuthenticated()` ne vérifie pas l'`exp` du JWT (flash + 401 évitable au démarrage) — P3.
- [ ] Logique métier (décision QrScanner vs List) dans `App.kt` → extraire un `SessionViewModel`/use-case.

**Sécurité / divers**
- [x] ~~Email loggé en clair au login~~ → **fait** : email retiré des logs de `AuthRepository.login` (PII).
- [ ] Pas de certificate pinning (à considérer pour une app prod manipulant des JWT).
- [ ] Pagination : suivre l'URL `next` plutôt que `?page=n` (P3 ; DRF est en PageNumber, OK aujourd'hui).
- [ ] `versionCode`/`versionName` figés (Android + iOS `Config.xcconfig` `TEAM_ID` vide).
- [ ] Trier les non-trackés : `composeApp/schema.yaml`, `gradle/gradle-daemon-jvm.properties`.

---

## ⚠️ Actions hors-code (Renaud)

- [ ] **Restreindre la clé API Firebase dans GCP** *(EN COURS — fait via la console GCP, projet `pushit-dcf8a`)* :
  - Restriction application → Android : package `com.foxugly.pushit_app` + SHA-1 **debug**
    `5E:13:E7:0A:58:5F:F4:50:E1:9B:66:46:32:5D:1E:0C:CB:35:25:8B`.
  - Restriction d'API : Firebase Installations API + Firebase Cloud Messaging API (+ Cloud Messaging).
  - **À compléter au moment de la signature release** : ajouter le SHA-1 du keystore *release* (sinon FCM
    cassé sur l'app publiée). Régénération de la clé non nécessaire (jamais atteint GitHub).
- [ ] **(optionnel) Activer le build APK en CI** — ajouter le secret `GOOGLE_SERVICES_JSON_B64`
  (base64 de `androidApp/google-services.json`) + la variable repo `HAS_FIREBASE_SECRET=true`. Sans ça, le
  job `build-debug-apk` de `.github/workflows/ci.yml` reste skippé (le job `test` tourne sans secret).
- [ ] **(optionnel) Doublon ?** — NON : `Foxugly/PushIT_frontend` est le **frontend web Angular** de
  PushIT, distinct de l'app mobile `Foxugly/PushIT_app`. Rien à supprimer.
