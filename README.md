# Feuille de route Eirbone (P2P)

**L'idée de base :** Dans un système Client-Serveur, si on veut une vidéo, on va sur un serveur central (comme YouTube). Si YouTube plante, on n'a plus rien. En P2P on a tous des bouts de fichiers de 1024 octets sur nos machines et on se les échange tous entre nous en direct.

### **Jira :** [Gestion de projet Eirbone](https://eirbone.atlassian.net/jira/software/projects/EIR/boards/1?atlOrigin=eyJpIjoiMDM5NWI5ZmQxZjM1NDQyMWJmMWM0OGU2NTE3YjRiMWQiLCJwIjoiaiJ9)

---

## 🛠️ Étape 1 : Le cœur du système

**I - Le tracker**

* **C'est quoi ?** C'est le registre central qui garde en mémoire qui a quoi.
* **Comment on fait ?** On part de `server.c`. Mais au lieu de juste faire un "écho", on va lui apprendre à lire !
* **Le but :** Quand il reçoit le texte `announce listenPort 6881 seed video.mp4`, il doit découper cette phrase, comprendre que le port est 6881, et sauvegarder ces infos dans une "structure" (un tableau de structs en C) pour s'en souvenir.

**II - Le peer "client"**

* **C'est quoi ?** Le programme qui s'annonce au tracker.
* **Comment on fait ?** On part de `Client.java`.
* **Le but :** Dès le lancement, ce programme doit lire notre fichier `config.ini` pour trouver l'adresse du tracker. Ensuite, il se connecte et envoie la vraie commande `announce`. **Surtout :** il ne doit pas faire de `.close()` tout de suite ! Il doit implémenter le "keep-alive" (garder la connexion ouverte en faisant une boucle infinie qui attend qu'on tape au clavier).

**III - Le peer "échangeur" et la donnée**

* **C'est quoi ?** La gestion des fichiers sur nos PC.
* **Comment on fait ?** On mélange la manipulation de fichiers et `Serveur.java`.
* **Le but :** Créer une classe qui prend un fichier, le découpe en morceaux de 1024 octets, calcule le "hash MD5" (l'empreinte digitale) de chaque morceau, et prépare tout ça pour qu'on puisse l'envoyer plus tard.

### 👥 Répartition :

* **L'équipe annuaire (2 personnes) :** Créer la `struct` en C pour représenter un Peer. Réussir à découper le message texte reçu avec les espaces, et remplir le tableau avec les bonnes infos.
* **L'équipe téléphone (2 personnes) :** Créer le lecteur du fichier `config.ini`. Coder le client Java pour qu'il envoie la vraie commande `announce` générée automatiquement avec les bonnes valeurs, sans jamais couper la connexion TCP (lkeep-alive).
* **L'équipe ciseaux (1 personne) :** Faire le découpage des fichiers locaux en pièces de 1024 octets ET commencer à regarder comment générer le MD5 d'une pièce en Java pour vérifier qu'elle n'est pas corrompue.

---

## 🚀 Étape 2 : Le mode multi-utilisateurs 

**I - Le multiplexage C**

* Le tracker actuel crashe ou bloque s'il y a deux personnes en même temps. L'équipe C va devoir rajouter la fonction `select()`. Ça permet au serveur d'écouter plusieurs sockets en même temps avec un seul thread.

**II - Le BufferMap et le binaire**

* **Le BufferMap :** Coder la chaîne (en Base64) qui résume toutes les pièces qu'on a.
* **La commande Data :** Réussir à envoyer un message qui contient à la fois du texte (l'en-tête de la commande) et du binaire pur (le bout du fichier .mp4 de 1024 octets) sans que ça bug.

**III - L'interface web**

* Créer une interface  simple qui affiche les fichiers disponibles et les pièces qu'on a. Et permet de lancer le téléchargement d'un fichier en cliquant dessus ou d'en ajouter un nouveau.


## Diagramme de Gantt :

| Séance | Wissal | Melissa | Dina | Nour | David |
| --- | --- | --- | --- | --- | --- |
| **13/03** | EIR-10 | EIR-11 | EIR-14 | EIR-1, EIR-4 | EIR-3, EIR-17 |
| **20/03** | EIR-10, EIR-12 | EIR-11, EIR-4 | EIR-14, EIR-15 | EIR-4, EIR-16 | EIR-17 |
| **24/03** | EIR-12 | EIR-12 | EIR-15, EIR-16 | EIR-16 | EIR-18 |
| **27/03** | EIR-13 | EIR-13 | EIR-16 | EIR-21 | EIR-19 |
| **03/04** | EIR-13 | EIR-13 | EIR-20 | EIR-21 | EIR-20 |
| **10/04** | EIR-22 | EIR-22 | EIR-22 | EIR-22 | EIR-22 |
| **21/04** | EIR-13 | EIR-13 | EIR-20 | EIR-21 | EIR-20 |
| **24/04** | EIR-22 | EIR-22 | EIR-20, EIR-22 | EIR-21, EIR-22 | EIR-20, EIR-22 |
| **28/04** | EIR-22 | EIR-22 | EIR-22 | EIR-22 | EIR-22 |
| **05/05** | Soutenance | Soutenance | Soutenance | Soutenance | Soutenance |