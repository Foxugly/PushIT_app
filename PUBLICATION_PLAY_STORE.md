# Publication sur le Google Play Store — étapes restantes

> Checklist opérateur pour publier **PushIT** (`com.foxugly.pushit_app`) sur le
> Play Store. L'app est **techniquement prête** (R8/ProGuard OK, build release
> configuré, API prod épinglée, FCM câblé, permissions/sécurité en place, APK
> minifié smoke-testé). Il reste à **signer**, **construire l'AAB**, et **créer
> la fiche Play Console**. Le détail signing est aussi dans `README.md`
> (section « Release build & signing »).

## ✅ Déjà fait (rien à refaire)
- `applicationId = com.foxugly.pushit_app`, `minSdk 24`, `targetSdk 36`,
  `compileSdk 37` (`androidApp/build.gradle.kts`, valeurs dans `gradle/libs.versions.toml`).
- Build `release` : R8 `isMinifyEnabled = true` + `proguard-rules.pro`
  (keep-rules kotlinx.serialization / Ktor / `PushItFirebaseService`).
- API **prod épinglée en release** : `https://pushit-api.foxugly.com/api/v1/`
  (`MainActivity.kt`) ; le switch de backend et les logs HTTP sont **debug-only**.
- Permissions : `INTERNET`, `CAMERA` (optionnelle, QR), `POST_NOTIFICATIONS`
  (consentement runtime Android 13+). `allowBackup=false`, HTTPS-only en prod.
- FCM/Firebase câblé (`PushItFirebaseService`, token provider, icône+couleur de
  notif `#10B981`). Icône adaptative + nom « PushIT ».
- `signingConfigs` release **conditionnel** (activé si `keystore.properties` /
  variables d'env présentes) — voir étapes 1–2.

---

## Étapes restantes

### A. Clés de signature & build signé

**1. Générer le keystore de release** (une seule fois, **à conserver à vie** —
le perdre = impossible de publier des mises à jour de l'app) :
```bash
keytool -genkeypair -v -keystore pushit-release.jks -alias pushit \
  -keyalg RSA -keysize 2048 -validity 10000
```
Stocke `pushit-release.jks` **hors du repo** (gestionnaire de secrets / coffre).
**Ne jamais committer** le keystore ni les mots de passe.

**2. Créer les secrets GitHub du dépôt** (`Foxugly/PushIT_app` → Settings →
Secrets and variables → Actions → *New repository secret*). Le workflow
`.github/workflows/release.yml` s'arrête dès le départ, en listant ceux qui
manquent :

| Secret | Contenu |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | le keystore d'upload encodé : `base64 -w0 pushit-release.jks` (PowerShell : `[Convert]::ToBase64String([IO.File]::ReadAllBytes("pushit-release.jks"))`) |
| `ANDROID_KEYSTORE_PASSWORD` | mot de passe du keystore |
| `ANDROID_KEY_ALIAS` | alias de la clé (ex. `pushit`) |
| `ANDROID_KEY_PASSWORD` | mot de passe de la clé |
| `GOOGLE_SERVICES_JSON` | le vrai `google-services.json` du projet Firebase PushIT (JSON brut ou base64) |
| `PUSHIT_SENTRY_DSN` | DSN du projet Sentry `pushit-app` (org `foxugly-srl`, région `de.sentry.io`) — à créer d'abord |

(Le secret `GOOGLE_SERVICES_JSON_B64` du workflow CI est distinct et optionnel : sans lui la CI compile
avec un placeholder. La release, elle, **refuse** le placeholder.)

**3. Version** : `pushit.versionName` dans `gradle.properties` (source unique) — à
bumper avant une release. Le `versionCode` n'est **plus écrit à la main** : en
release c'est le numéro de run du workflow (`GITHUB_RUN_NUMBER`), qui augmente à
chaque exécution. **N'uploader sur Play que des AAB produits par ce workflow** : un
build local prend le nombre de commits comme `versionCode` et pourrait passer
devant le compteur CI (sinon, forcer `PUSHIT_VERSION_CODE`). Détail : `CLAUDE.md` → *Versioning*.

**4. Lancer le build signé** : Actions → *Release* → *Run workflow* (ou pousser un
tag : `git tag v1.0.0 && git push origin v1.0.0`). Le job lance les tests, construit
`:androidApp:bundleRelease` signé, vérifie la signature (`jarsigner`) et publie en
**artifact** `pushit-<versionName>-<run>` : `androidApp-release.aab` + `mapping.txt`
(à garder pour désobfusquer les stack traces). **Aucun upload Play automatique.**

**5. (Alternative locale)** — build signé sur le PC, pour un test uniquement :
`keystore.properties` à la racine (git-ignored) avec `RELEASE_STORE_FILE`,
`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, le vrai
`androidApp/google-services.json`, puis `.\gradlew.bat :androidApp:bundleRelease`
→ `androidApp/build/outputs/bundle/release/androidApp-release.aab`.

**6. Empreintes SHA : rien à déclarer dans *Firebase* pour les push.** FCM
n'utilise **pas** les empreintes enregistrées dans la console Firebase : elles ne
servent qu'aux services qui authentifient l'app par son certificat (Google
Sign-In / Firebase Auth, Dynamic Links, App Check avec Play Integrity) — **aucun
n'est utilisé par PushIT**.

⚠️ En revanche, si la **clé API Android** est restreinte aux apps Android dans
GCP (recommandé, voir `README.md` → *Securing the Firebase API key*), le SHA-1
du certificat qui signe l'APK **installé** doit figurer dans cette restriction,
sinon l'enregistrement FCM échoue en prod. Avec Play App Signing, ce certificat
est la **clé d'app gérée par Google** (Play Console → Intégrité de l'app), à
ajouter en plus de la clé d'upload :
`keytool -list -v -keystore pushit-release.jks -alias pushit` → GCP Console →
projet `pushit-dcf8a` → APIs & Services → Credentials → clé Android →
Application restrictions → Android apps.

### B. Google Play Console

**7. Créer l'application** (Play Console → Créer une application) : nom
« PushIT », langue par défaut, type *Application*, *Gratuite*. Activer **Play
App Signing** (recommandé : Google gère la clé d'app ; tu fournis ta clé
d'**upload** = le keystore ci-dessus).

**8. Fiche du Store** (Présence sur le Store → Fiche principale) — les textes et
visuels sont **dans le repo**, au format fastlane `supply` :
`fastlane/metadata/android/{fr-FR,nl-NL,en-US}/`
- `title.txt` (≤ 30 car.), `short_description.txt` (≤ 80), `full_description.txt` (≤ 4000).
- `images/icon.png` **512×512** et `images/featureGraphic.png` **1024×500** (émeraude `#10b981`),
  générés depuis les icônes de l'app par `python fastlane/generate_store_assets.py` (Pillow).
- **TODO — captures d'écran** (≥ 2 téléphone, à faire sur un appareil/émulateur avec un compte de
  démo) : inbox non lus, dossiers par application, détail d'une notification, réglages. À déposer
  dans `fastlane/metadata/android/<locale>/images/phoneScreenshots/` (1.png, 2.png…).
- Catégorie (*Productivité* ou *Communication*), coordonnées (email).

**9. Politique de confidentialité** : URL publique **https://pushit.foxugly.com/privacy**
(route SPA du frontend). Vérifier qu'elle mentionne : tokens FCM, contenu des
notifications, JWT stocké chiffré, backend `pushit-api.foxugly.com`, Sentry
(rapports de crash sans PII), durées de conservation ci-dessous.

**10. Sécurité des données (Data safety)** : déclarer
- **Données collectées** : adresse e-mail et identifiant utilisateur (gestion du
  compte) ; identifiants de l'appareil = token FCM (fonctionnement de l'app :
  livraison des push) ; contenu des notifications reçues (fonctionnement de
  l'app) ; journaux de plantage / diagnostics via Sentry (analyse des plantages,
  **sans PII** : pas d'IP, pas d'utilisateur, pas de jetons). La caméra (scan du
  QR) est traitée **sur l'appareil** (ML Kit) : aucune image n'est collectée.
  Aucune publicité, aucune analytics, aucun suivi.
- **Chiffré en transit** : oui (HTTPS uniquement). **Partage avec des tiers** :
  non (Google/Firebase et Sentry agissent comme sous-traitants pour le compte de
  Foxugly, ce que Play ne compte pas comme un partage).
- **Suppression des données : oui.**
  - **Dans l'app** : Réglages → *Supprimer mon compte* (mot de passe demandé) →
    `DELETE /api/v1/auth/me/`. Supprime le compte, ses appareils et les
    applications qu'il possède, avec leurs notifications et données.
  - **Sur le web** (exigé par Play : URL de demande de suppression utilisable sans
    installer l'app) : la console `pushit.foxugly.com` appelle la même route
    `DELETE /auth/me/`. Renseigner l'URL de la page de suppression de compte de la
    console une fois publiée (à défaut : une demande par e-mail via la page de
    contact, traitée par l'admin).
- **Conservation** : les notifications sont purgées automatiquement après
  **12 mois** (`RETENTION_NOTIFICATION_DAYS=365` côté serveur, purge quotidienne) ;
  le reste vit tant que le compte existe et disparaît à sa suppression.

**11. Classification du contenu (IARC)** : remplir le questionnaire (app
utilitaire → réponses « non » : violence, contenu sexuel, etc.).

**12. Public cible & contenu** + **Annonces** : *aucune publicité*. **Accès à
l'app** : le login étant requis, fournir un **compte de test** aux réviseurs.

### C. Tests puis publication

**13. Piste de test interne d'abord** (Tests → Test interne) : créer une
release, **uploader l'AAB**, ajouter des testeurs, installer via le lien Play et
**valider sur un appareil réel** : login, scan d'un QR, réception d'une
notification push (valide aussi la restriction SHA de la clé API, étape 6),
suppression d'un compte de test.

**14. Promouvoir en Production** : créer la release de production à partir de
l'AAB validé, notes de version, **déploiement progressif** (ex. 20 %), puis
envoi en revue. Première revue Google : quelques heures à quelques jours.

---

## Pièges à connaître
- **Le keystore est irremplaçable** : sa perte empêche toute mise à jour. Sauvegarde-le.
- **AAB, pas APK** pour une nouvelle app sur le Store.
- **Clé API restreinte aux apps Android** : y ajouter le SHA-1 de la clé d'app Play (étape 6).
- `google-services.json` et `keystore.properties` restent **hors git**.
- `versionCode` doit **strictement augmenter** à chaque upload → n'uploader que les AAB du workflow *Release*.

## Commandes récap
```bash
# build signé (AAB) : Actions → Release → Run workflow (ou tag v*)
# local, test uniquement :
.\gradlew.bat :androidApp:bundleRelease
# (debug, pour mémoire) installer sur émulateur
.\gradlew.bat :androidApp:installDebug
```
