# Feuille de Route de l'équipe

**Le principe du projet :** Dans un système réseau classique (Client-Serveur), si tu veux télécharger une vidéo, tu vas sur YouTube (le Serveur central). Si YouTube tombe en panne, personne n'a la vidéo. Dans notre projet (en P2P), tout le monde a des bouts de données de 1024 octets et on se les repartage entre nous. 

---

## 🛠️ Première itération : Construire les fondations (Tests manuels)

**I - Le Tracker (Le centre de contrôle)**

* **C'est quoi ?** L'annuaire central qui sait qui possède quelle donnée. Il n'a aucun fichier, il a juste un registre. Il tourne sans s'arrêter et attend des messages textes.
* **Comment ?** On doit le coder en C. On va copier le fichier `server.c` du cours (qui ouvre un port avec `bind()` et écoute avec `listen()`).
* **À faire :** Une fois la connexion réussie, il faut coder de quoi lire les mots envoyés par les Peers (comme "look" ou "announce") et les sauvegarder dans une liste en mémoire.

**II - Le Peer "Client" (Celui qui demande)**

* **C'est quoi ?** La partie de notre programme qui parle au Tracker pour s'annoncer ou chercher un fichier.
* **Comment ?** On copie le fichier `Client.java` du prof (qui crée un `Socket` pour se brancher au Tracker).
* **À faire :** Au lieu d'envoyer "bonjour", on doit lui faire envoyer les phrases exactes du sujet, par exemple : `announce listenPort 6881 seed video.mp4`.

**III - Le Peer "Échangeur" (Celui qui partage)**

* **C'est quoi ?** La partie qui permet à notre ordinateur d'envoyer des bouts de fichiers directement aux copains (sans passer par le Tracker).
* **Comment ?** On utilise le fichier `Serveur.java` du prof pour utiliser un `ServerSocket`.
* **À faire :** Écrire une fonction Java qui prend un fichier sur le disque dur, le coupe en petits morceaux de 1024 octets, et les envoie dans le tuyau quand un autre Peer le demande.

### 👥 Les Rôles pour la Phase 1 :

* **L'équipe Annuaire (2 personnes - C) :** Ils ouvrent `server.c`. Leur but cette semaine est de réussir à le compiler, le lancer, et faire en sorte qu'il affiche à l'écran les messages reçus.
* **L'équipe Téléphone (2 personnes - Java) :** Elles ouvrent `Client.java` et `Serveur.java`. Leur but : faire tourner le Serveur sur un PC, le Client sur un autre, et réussir à s'envoyer le mot "Coucou" sur le réseau.
* **L'équipe Ciseaux (1 personne - Java) :** Elle oublie le réseau ! Elle code un programme Java classique qui ouvre un fichier, le découpe en morceaux de 1024 octets, et teste de le reconstruire.

---

## 🚀 Deuxième itération : Le vrai protocole et l'automatisation

*(Une fois que nos tests avec "Coucou" fonctionnent, on passe aux choses sérieuses)*

**I - Configurations & Variables**

* **Fichier config :** Finies les adresses IP écrites en dur dans le code. On doit lire le fichier `config.ini` pour savoir sur quel port le Tracker écoute et où sauvegarder les fichiers.

**II - Protocole & Multiplexage (Le gros du travail)**

* **Les commandes :** Implémentation complète des commandes texte du sujet (`announce`, `look`, `getpieces`, `update`).
* **Le BufferMap :** Coder la chaîne de caractères (en Base64) qui indique quels morceaux de fichiers on possède pour l'envoyer aux autres.
* **Le Multiplexage C :** Le Tracker doit gérer des dizaines de connexions. Le prof exige d'utiliser la fonction `select()` en C pour surveiller toutes les portes d'entrée en même temps sans crasher.
* **Le Keep-Alive :** Ne pas fermer la connexion TCP avec `close()` après chaque message, mais la garder ouverte.

**III - Interface Utilisateur**

* **Serveur HTTP :** Créer un petit script (le prof autorise Python) pour embarquer une interface Web qui affiche nos téléchargements en cours joliment dans un navigateur.

### 👥 Les Rôles pour la Phase 2 :

* **L'Architecte C (Tracker) :** Gère la boucle `select()` dans le Tracker et le tableau d'enregistrement des Peers en mémoire.
* **L'Expert Réseau Java :** Gère l'envoi/réception de la commande `data` (qui mélange le texte et les données binaires du fichier).
* **Le "Traducteur" (Parsing) :** Traite les chaînes de caractères (Regex, split) pour extraire les bonnes informations des messages textes envoyés sur le réseau.
* **Le Gestionnaire de Données :** Gère l'algorithmique du BufferMap, la génération du hash MD5 et la vérification des fichiers téléchargés.
* **L'Intégrateur / Chef de projet :** Code le serveur HTTP/Python, gère le lecteur de `config.ini` et rassemble nos codes sur Thor (Git) pour préparer la démo de la séance 6.
