# Mise en service

## 1. Source de données
Obtenir un contrat/API Trading Economics autorisant le calendrier, le streaming et la redistribution dans une application publique. Valider les quotas avant de choisir POLL_MS. Aucun achat n'a été effectué.

## 2. Serveur
Node.js 22+, instance persistante, HTTPS via l'hébergeur/reverse proxy.
```sh
cd server
npm install
cp .env.example .env
# Renseigner .env hors Git puis :
node --env-file=.env src/server.mjs
```
Conserver package-lock.json après installation. Le processus utilise un volume DATA_DIR en lecture/écriture, avec une seule instance. Ne pas déployer le timer sur une fonction éphémère.
Configurer HTTPS, limitation des requêtes au reverse proxy, supervision de /health et politique de logs. /events diffuse uniquement le calendrier normalisé ; aucune route publique ne peut envoyer de notifications.
Le rafraîchissement complet s'effectue par défaut toutes les 60 secondes ; WebSocket fournit les résultats entre les rafraîchissements. Les reminders sont évalués toutes les 10 secondes. Un calendrier périmé bloque les rappels.
Les secrets restent dans les variables d'environnement/gestionnaire de secrets. Ne jamais les coller dans une issue publique.

## 3. Firebase
Créer un projet Firebase, enregistrer l'application Android com.ecoalert.app. Installer les Application Default Credentials côté serveur et activer ENABLE_PUSH=true.
Fournir à Gradle les paramètres clients Firebase (issus de la configuration de l'application) :
```properties
API_BASE_URL=https://votre-serveur.example
FIREBASE_APP_ID=configuration-client
FIREBASE_API_KEY=configuration-client
FIREBASE_PROJECT_ID=configuration-client
FIREBASE_SENDER_ID=configuration-client
```
À stocker dans ~/.gradle/gradle.properties ou dans les paramètres CI de la compilation connectée ; aucun fichier de compte de service dans l'APK.
La compilation CI fournie ne passe pas ces paramètres : elle produit intentionnellement la démonstration.
Valider sur téléphone que les abonnements FCM sont synchronisés et que les alertes restent désactivées en cas de refus de permission.

## 4. Validation avant diffusion
- Comparer sur une publication réelle les heures, le consensus, le précédent révisé et le réel avec la source.
- Tester rappel 5/15/30 minutes et résultat avec écran fermé, Doze et permissions refusées/révoquées.
- Tester coupure réseau, cache périmé, événement reporté/annulé et redémarrage serveur.
- Tester Android 8, 13 et 16, grandes polices et lecteur d'écran.
- Vérifier quotas, droits de redistribution et coûts du fournisseur/hébergement.

## 5. Play Store
Préparer un compte Play Console, identité éditeur, signature de production privée, icône adaptative finale, captures et fiche française.
Adapter et héberger la politique de confidentialité : [brouillon](PRIVACY.md).
Compléter les déclarations Play Console selon la collecte réellement activée, puis le parcours de test exigé par le compte.
Vérifier les exigences Play applicables au moment du dépôt. Le bundle produit ici n'est pas signé ; la version de production doit être signée avec la clé d'envoi conservée hors dépôt.
L'application n'est pas publiée et aucune dépense n'a été engagée.
