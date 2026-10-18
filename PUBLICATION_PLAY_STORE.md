# Publication sur le Google Play Store — étapes restantes

> Checklist opérateur pour publier **PushIT** (`com.foxugly.pushit_app`) sur le
> Play Store. L'app est **techniquement prête** (R8/ProGuard OK, build release
> configuré, API prod épinglée, FCM câblé, permissions/sécurité en place, APK
> minifié smoke-testé). Il reste à **signer**, **construire l'AAB**, et **créer
> la fiche Play Console**. Le détail signing est aussi dans `README.md`
> (section « Release build & signing »).

## ✅ Déjà fait (rien à refaire)
- `applicationId = com.foxugly.pushit_app`, `minSdk 24`, `targetSdk 36`,
  `compileSdk 36` (`androidApp/build.gradle.kts`).
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

**6. Enregistrer l'empreinte SHA de la clé release dans Firebase** (sinon **FCM
est cassé sur le build signé** — les notifications n'arriveront pas) :
```bash
keytool -list -v -keystore pushit-release.jks -alias pushit
```
Copier **SHA-1** et **SHA-256** → console Firebase → Paramètres du projet → ton
app Android → « Ajouter une empreinte ». (Si tu utilises **Play App Signing**,
ajoute AUSSI le SHA-1 de la **clé d'app gérée par Google**, visible dans Play
Console → Configuration → Intégrité de l'app, une fois l'app créée.)

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

**9. Politique de confidentialité** : URL **publique** obligatoire (héberge une
page ; mentionne : tokens FCM, notifications, JWT stocké chiffré, backend
`pushit-api.foxugly.com`).

**10. Sécurité des données (Data safety)** : déclarer
- Identifiants/jetons : token FCM (fonctionnement de l'app), JWT en
  `EncryptedSharedPreferences`.
- **Chiffré en transit** : oui (HTTPS). **Partage avec des tiers** : non (hors
  Google/Firebase pour le push). Mécanisme de suppression des données : oui.

**11. Classification du contenu (IARC)** : remplir le questionnaire (app
utilitaire → réponses « non » : violence, contenu sexuel, etc.).

**12. Public cible & contenu** + **Annonces** : *aucune publicité*. **Accès à
l'app** : le login étant requis, fournir un **compte de test** aux réviseurs.

### C. Tests puis publication

**13. Piste de test interne d'abord** (Tests → Test interne) : créer une
release, **uploader l'AAB**, ajouter des testeurs, installer via le lien Play et
**valider sur un appareil réel** : login + réception d'une notification push
(important car le build Play-signé utilise la clé d'app de Google → d'où l'étape
6 pour le SHA).

**14. Promouvoir en Production** : créer la release de production à partir de
l'AAB validé, notes de version, **déploiement progressif** (ex. 20 %), puis
envoi en revue. Première revue Google : quelques heures à quelques jours.

---

## Pièges à connaître
- **Le keystore est irremplaçable** : sa perte empêche toute mise à jour. Sauvegarde-le.
- **AAB, pas APK** pour une nouvelle app sur le Store.
- **SHA release dans Firebase** sinon les pushs ne marchent pas sur le build signé.
- `google-services.json` et `keystore.properties` restent **hors git**.
- `versionCode` doit **strictement augmenter** à chaque upload → n'uploader que les AAB du workflow *Release*.

## Commandes récap
```bash
# build signé (AAB) : Actions → Release → Run workflow (ou tag v*)
# local, test uniquement :
.\gradlew.bat :androidApp:bundleRelease
# empreintes pour Firebase
keytool -list -v -keystore pushit-release.jks -alias pushit
# (debug, pour mémoire) installer sur émulateur
.\gradlew.bat :androidApp:installDebug
```
