# BACKLOG — PushIT_app (client KMP)

Backlog issu de l'audit complet du 2026-06-12 (sécurité, réseau/API, archi/UI, code
plateforme Android/iOS, build/tests/hygiène). Sévérités : **P0** bloquant · **P1** important ·
**P2/P3** à nettoyer. Les items cochés ont été traités le 2026-06-12 (non commités — le repo
n'a pas encore de remote `Foxugly/PushIT_frontend`).

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

## 🔴 P0 — Bloquants restants

- [ ] **iOS — push totalement cassé** *(Renaud : iOS pas encore géré)*
  - `iosApp/iosApp/iOSApp.swift:34-38` : `didReceiveRegistrationToken` est un **stub vide** → le
    token FCM n'arrive jamais au Kotlin `FcmTokenProvider` → aucun device iOS enregistré. Bridger le
    token (exposer `FcmTokenProvider.instance.updateToken(token)` depuis le framework) + récupérer le
    token initial au démarrage (équivalent du `refreshToken()` Android, manquant côté iOS).
  - `iosApp/iosApp/Info.plist` : manque `GoogleService-Info.plist` (→ `FirebaseApp.configure()` **crash**
    au lancement), `UIBackgroundModes = remote-notification`, entitlement `aps-environment` (APNs),
    et `NSCameraUsageDescription` (dès l'implémentation réelle du scanner QR).
- [ ] **Release Android non signé + non minifié** — `androidApp/build.gradle.kts:32-36` :
  `isMinifyEnabled = false`, aucun `signingConfig`. Build release non distribuable (Play refuse), code
  non obfusqué (JWT/endpoints lisibles après décompilation). Ajouter un `signingConfig` (keystore
  **hors-VCS**, identifiants via `local.properties`/env) + `isMinifyEnabled = true` + `proguardFiles`
  AVEC keep-rules kotlinx.serialization (`@Serializable`) et Firebase. *(Non bloquant tant qu'il n'y a
  pas de release Store ; debug non impacté.)*

---

## 🟠 P1 — Restants (décision / infra)

- [x] ~~**Turnstile vs mobile**~~ → **tranché : mobile login-only** (voir ci-dessus).
- [x] ~~**Remote + push**~~ → **fait** : poussé sur **`Foxugly/PushIT_app`** (public, `main`), historique
  scrubé de `google-services.json` avant push, CI vert. *(NB : `Foxugly/PushIT_frontend` est le frontend
  **web Angular** de PushIT — projet distinct, pas l'app mobile.)*
- [ ] **CI build APK** *(action Renaud, optionnel)* — pour activer le job `build-debug-apk`, ajouter le secret
  `GOOGLE_SERVICES_JSON_B64` + la variable repo `HAS_FIREBASE_SECRET=true`.
- [ ] **Couverture de tests (suite)** — `AuthInterceptor`/`PushItApi` 401 couverts ; reste à tester
  `AuthRepository`, `NotificationRepository`, `DeviceLinkManager` (le seam `TokenStore` + `MockEngine` est désormais en place).
- [ ] **Unlink côté serveur** — le bouton « Unlink this device » efface l'app-token *local* uniquement ;
  le serveur garde la liaison device↔application. Ajouter un appel d'API de déliaison (endpoint backend à prévoir).

---

## 🟡 P2 / P3 — Restants (différés, avec raison)

**iOS** *(plateforme gérée séparément — non compilable sous Windows)*
- [ ] Tokens iOS en `NSUserDefaults` non chiffré → **Keychain**.
- [ ] `@Volatile`/synchro sur `currentToken` iOS (fait côté Android).
- [ ] Scanner QR iOS = stub qui spamme `onError` → message neutre.
- [ ] Pas de cible de test iOS (`commonTest` ne tourne que sur androidHostTest).

**Lourds / à valider séparément**
- [ ] **i18n totalement absente** — chaînes EN en dur ; externaliser via `compose.resources` (chantier dédié).
- [ ] `material3 = 1.10.0-alpha05` → stable : *la version stable cible n'est pas évidente (les material3 JetBrains
  sont versionnés à part de Compose 1.10.3) ; bump à valider par un build complet, pas fait à l'aveugle.*
- [ ] `Notification.status` en `enum` : *gardé en `String` volontairement — un `enum` kotlinx planterait si le
  serveur ajoute une valeur (`StatusEnum`) ; nécessiterait un fallback. À faire avec soin.* (Fixture test `"delivered"` à corriger en passant.)
- [ ] Parsing timestamp `split("T")` → `kotlinx-datetime` (fonctionne aujourd'hui ; polish).
- [ ] `isAuthenticated()` ne vérifie pas l'`exp` du JWT (flash + 401 évitable au démarrage) — P3.
- [ ] Logique métier (décision QrScanner vs List) dans `App.kt` → extraire un `SessionViewModel`/use-case.

**Sécurité / divers**
- [ ] Email loggé en clair au login (`AuthRepository.login`) — PII Logcat ; conditionner à un flag debug (commonMain n'a pas de `BuildConfig`).
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
