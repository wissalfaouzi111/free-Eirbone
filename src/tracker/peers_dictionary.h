#ifndef __PEERS_DICTIONARY_H__
#define __PEERS_DICTIONARY_H__

#include "file.h"

#define MAX_PEERS_PAR_FICHIER 1000
#define MAX_ENTREES_DICTIONNAIRE 100
#define TAILLE_IP 16  // "255.255.255.255\0"

typedef struct {
    char ip[TAILLE_IP];
    int port;
} PeerInfo;

typedef struct {
    char cle_md5[TAILLE_MD5];        // Clé MD5 du fichier
    PeerInfo peers[MAX_PEERS_PAR_FICHIER];  // Liste des peers pour ce fichier
    int nb_peers;                     // Nombre de peers actuellement enregistrés
} EntreeDictionnaire;

// Dictionnaire global : tableau d'entrées
extern EntreeDictionnaire dictionnaire[MAX_ENTREES_DICTIONNAIRE];
extern int nb_entrees;

/**
 * Ajoute un peer pour un fichier donné (identifié par son MD5)
 * Retourne 0 si succès, -1 si erreur (dictionnaire plein, etc.)
 */
int ajouter_peer(const char *cle_md5, const char *ip, int port);

/**
 * Récupère tous les peers pour un fichier donné
 * Retourne NULL si le fichier n'existe pas, sinon retourne un pointeur à l'entrée du dictionnaire
 */
EntreeDictionnaire* get_peers(const char *cle_md5);

/**
 * Supprime un peer spécifique d'un fichier
 * Retourne 0 si succès, -1 si le peer ou le fichier n'existe pas
 */
int supprimer_peer(const char *cle_md5, const char *ip, int port);

/**
 * Supprime tous les peers d'un fichier
 */
int vider_peers(const char *cle_md5);

/**
 * Affiche tous les peers pour un fichier donné (pour debug)
 */
void afficher_peers(const char *cle_md5);

#endif /* __PEERS_DICTIONARY_H__ */
