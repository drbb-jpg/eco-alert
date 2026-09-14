# Éco Alert

Application Android en français pour suivre les annonces économiques des États-Unis et de la zone euro.

**État : première version de développement.** Sans configuration, l'APK est une démonstration avec chiffres fictifs et alertes indisponibles. Aucun calendrier réel, serveur public ni publication Play Store n'est activé par ce dépôt.

## Fonctionnalités
- Aujourd'hui et semaine du lundi au dimanche ; heure du Maroc, téléphone ou UTC.
- Filtres pays et importance, cartes colorées, précédent / consensus / réel, révisions.
- Comparaison numérique descriptive, sans signal d'achat ou de vente.
- Rappels facultatifs 5, 15 ou 30 minutes avant ; notification du premier résultat.
- Mode connecté : actualisation de l'écran toutes les 15 secondes, cache hors ligne clairement signalé.
- Serveur : calendrier Trading Economics, réception WebSocket des résultats, Firebase Cloud Messaging, état persistant.

## Essayer l'APK
Dans GitHub, ouvrir **Actions → Android and server checks → une exécution réussie → Artifacts → eco-alert-demo-apk**. Décompresser puis installer l'APK sur un téléphone Android 8 ou supérieur. L'installation hors Play Store nécessite l'autorisation Android correspondante.
Le fichier AAB produit est **non signé**, destiné à la préparation de publication, pas directement installable.

## Développement
JDK 17, Gradle 8.13 et SDK Android 36. Ouvrir le dépôt dans Android Studio ou utiliser Gradle installé :
```sh
gradle :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
cd server
npm test
```
Le wrapper Gradle binaire n'est pas inclus ; le workflow installe une version fixe.
Sources Java natives, sans framework web.

## Activer les données et notifications
Voir [le guide de configuration](docs/SETUP.md). La clé fournisseur et les identifiants administrateur Firebase restent exclusivement sur le serveur.
Les préférences sont locales et désactivées par défaut. Les filtres du calendrier n'affectent pas les abonnements : les alertes couvrent toutes les annonces majeures US/zone euro.

## Limites connues
- Aucun essai sur appareil ni validation avec identifiants Trading Economics/Firebase n'a encore été réalisé.
- Les noms d'indicateurs du fournisseur peuvent rester en anglais ; l'interface est française.
- L'envoi immédiat et la réception à la seconde ne sont pas garantis. L'écran connecté interroge le serveur toutes les 15 secondes.
- Les événements sans résultat numérique peuvent rester « Résultat en attente ». Les discours sans résultat ne déclenchent pas de notification de résultat.
- Serveur initial mono-instance : volume persistant obligatoire. En cas de crash pendant un envoi, une alerte peut être perdue ; aucun mécanisme ne peut garantir exactement une réception côté téléphone.
- Les dépendances directes sont fixées ; générer et conserver un package-lock après installation avant déploiement de production.

## Références techniques
- [Calendrier Trading Economics](https://docs.tradingeconomics.com/economic_calendar/country/)
- [Flux de résultats](https://docs.tradingeconomics.com/economic_calendar/streaming/)
- [Notifications Firebase Android](https://firebase.google.com/docs/cloud-messaging/android/get-started)
- [Compatibilité Android Gradle Plugin](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
