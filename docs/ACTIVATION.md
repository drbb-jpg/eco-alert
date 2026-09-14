# Activer la version connectée

État : configuration préparée, aucun service provisionné. Notifications exclusivement Android par Firebase Cloud Messaging ; aucun envoi par e-mail ou SMS.

## Accès à obtenir

1. Connecter Render à ChatGPT et au dépôt GitHub eco-alert. La proposition dans deploy/render.yaml utilise un service Starter et un disque persistant de 1 Go, donc des ressources payantes. Examiner le coût affiché avant création. L'abonnement Trading Economics est distinct.
2. Créer un projet dans https://console.firebase.google.com/ puis enregistrer une application Android de nom de package exact com.ecoalert.app. Google Analytics n'est pas nécessaire. Télécharger le fichier CLIENT google-services.json pour préparer la compilation Android. Ne pas confondre ce fichier client avec une clé privée de compte de service.
3. Obtenir un accès Trading Economics Calendar + Streaming avec un quota compatible (par défaut 1 requête/minute, soit environ 43 200 requêtes par 30 jours, plus streaming), couvrant États-Unis/zone euro et autorisant la redistribution visée. Ne pas acheter un abonnement au site sans vérifier qu'il donne accès à ces API.

## Serveur

Importer le Blueprint deploy/render.yaml depuis la branche feature/android-calendar après validation du coût et des droits fournisseur.
Renseigner TE_CREDENTIAL directement dans l'espace de secrets Render. Le fichier versionné ne contient aucune valeur privée.
Le serveur commence avec ENABLE_PUSH=false. Vérifier /health et /events : dates UTC, consensus distinct de la projection fournisseur, valeurs officielles et révisions.
La configuration est une proposition à valider par Render ; elle n'a pas encore été déployée ni testée avec des identifiants réels.

## Firebase serveur

Le serveur existant utilise les Application Default Credentials.
Dans un environnement de production, privilégier un compte de service dédié au seul envoi FCM et des identifiants fédérés lorsqu'ils sont disponibles.
Pour la configuration par fichier prise en charge ici, ajouter la clé de ce compte dans Render > Environment > Secret Files sous le nom firebase-admin.json. Son chemin devient /etc/secrets/firebase-admin.json.
Ne jamais placer la clé privée dans GitHub, dans l'APK ou dans une conversation.
Après mise en place des droits et du fichier, définir ENABLE_PUSH=true et redéployer.
Le nom de projet doit correspondre à celui de l'application Android.

## Compilation Android connectée

Extraire du fichier CLIENT google-services.json les correspondances suivantes :

| Paramètre Gradle | Champ du fichier client |
|---|---|
| FIREBASE_PROJECT_ID | project_info.project_id |
| FIREBASE_SENDER_ID | project_info.project_number |
| FIREBASE_APP_ID | client correspondant à com.ecoalert.app : client_info.mobilesdk_app_id |
| FIREBASE_API_KEY | même client : api_key[].current_key |
| API_BASE_URL | URL HTTPS effective retournée par Render |

Passer ces cinq paramètres au build Gradle via un fichier de configuration local non publié ou des paramètres CI. Le workflow de démonstration actuel ne les prend pas encore en charge : il faut préparer une compilation connectée une fois ces valeurs connues.
Ne pas remplacer FIREBASE_APP_ID par le nom de package.

## Vérification avant mise à disposition

Installer l'APK connecté sur le téléphone de test. Vérifier d'abord le calendrier, puis activer volontairement les notifications et accepter la permission Android. Tester les rappels, les résultats, le refus de permission, la désactivation et la réception en arrière-plan.
L'autorisation du présent développement ne constitue pas une autorisation d'envoyer une notification de test à tous les utilisateurs : cibler seulement le téléphone de test.
Les notifications FCM et la connexion du téléphone ne garantissent pas une livraison à la seconde.
Aucune publication Play Store n'est incluse dans cette activation.

## Documentation officielle

- https://render.com/docs/blueprint-spec
- https://render.com/docs/configure-environment-variables
- https://firebase.google.com/docs/admin/setup
- https://firebase.google.com/docs/projects/api-keys
- https://docs.tradingeconomics.com/economic_calendar/streaming/
