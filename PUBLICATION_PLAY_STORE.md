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

**2. Créer `keystore.properties`** à la racine du repo (déjà git-ignored) :
```properties
RELEASE_STORE_FILE=/chemin/absolu/pushit-release.jks
RELEASE_STORE_PASSWORD=********
RELEASE_KEY_ALIAS=pushit
RELEASE_KEY_PASSWORD=********
```
(ou les variables d'env équivalentes `RELEASE_STORE_FILE`, etc.)

**3. Placer `google-services.json`** dans `androidApp/` (téléchargé depuis la
console Firebase du projet PushIT ; déjà git-ignored). Sans lui, le build
release échoue / FCM ne s'initialise pas.

**4. Bumper la version** dans `androidApp/build.gradle.kts` :
- `versionCode = 1` → **incrémenter à chaque upload** (ex. `1`).
- `versionName = "1.0"` → libellé visible (ex. `"1.0.0"`).

**5. Construire l'App Bundle (AAB)** — le Play Store **exige un AAB**, pas un APK :
```bash
.\gradlew.bat :androidApp:bundleRelease
```
Sortie : `androidApp/build/outputs/bundle/release/androidApp-release.aab`.
(La tâche `bundleRelease` est fournie automatiquement par l'Android Gradle
Plugin pour un module application — vérifier qu'elle existe :
`.\gradlew.bat :androidApp:tasks | findstr bundle`.)

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

**8. Fiche du Store** (Présence sur le Store → Fiche principale) :
- Icône **512×512** PNG, **feature graphic 1024×500**.
- **≥ 2 captures d'écran** téléphone (ex. liste des notifs, détail, réglages).
- Titre, **description courte (80 car.)**, **description complète (4000 car.)**.
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
- `versionCode` doit **strictement augmenter** à chaque upload.

## Commandes récap
```bash
# build signé (AAB)
.\gradlew.bat :androidApp:bundleRelease
# empreintes pour Firebase
keytool -list -v -keystore pushit-release.jks -alias pushit
# (debug, pour mémoire) installer sur émulateur
.\gradlew.bat :androidApp:installDebug
```
