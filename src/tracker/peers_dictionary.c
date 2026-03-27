#include "peers_dictionary.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

EntreeDictionnaire dictionnaire[MAX_ENTREES_DICTIONNAIRE];
int nb_entrees = 0;

/**
 * Cherche une entrée du dictionnaire par MD5
 * Retourne l'index si trouvée, -1 sinon
 */
static int trouver_entree(const char *cle_md5) {
    for (int i = 0; i < nb_entrees; i++) {
        if (strcmp(dictionnaire[i].cle_md5, cle_md5) == 0) {
            return i;
        }
    }
    return -1;
}

/**
 * Crée une nouvelle entrée dans le dictionnaire
 * Retourne l'index de la nouvelle entrée, -1 si le dictionnaire est plein
 */
static int creer_entree(const char *cle_md5) {
    if (nb_entrees >= MAX_ENTREES_DICTIONNAIRE) {
        return -1;
    }

    strncpy(dictionnaire[nb_entrees].cle_md5, cle_md5, TAILLE_MD5 - 1);
    dictionnaire[nb_entrees].cle_md5[TAILLE_MD5 - 1] = '\0';
    dictionnaire[nb_entrees].nb_peers = 0;

    return nb_entrees++;
}

int ajouter_peer(const char *cle_md5, const char *ip, int port) {
    // Chercher ou créer l'entrée pour ce fichier
    int index = trouver_entree(cle_md5);
    if (index == -1) {
        index = creer_entree(cle_md5);
        if (index == -1) {
            fprintf(stderr, "Erreur: dictionnaire plein\n");
            return -1;
        }
    }

    // Vérifier si ce peer existe déjà
    EntreeDictionnaire *entree = &dictionnaire[index];
    for (int i = 0; i < entree->nb_peers; i++) {
        if (strcmp(entree->peers[i].ip, ip) == 0 && entree->peers[i].port == port) {
            // Peer déjà existant
            return 0;
        }
    }

    // Ajouter le nouveau peer
    if (entree->nb_peers >= MAX_PEERS_PAR_FICHIER) {
        fprintf(stderr, "Erreur: trop de peers pour le fichier %s\n", cle_md5);
        return -1;
    }

    strncpy(entree->peers[entree->nb_peers].ip, ip, TAILLE_IP - 1);
    entree->peers[entree->nb_peers].ip[TAILLE_IP - 1] = '\0';
    entree->peers[entree->nb_peers].port = port;
    entree->nb_peers++;

    return 0;
}

EntreeDictionnaire* get_peers(const char *cle_md5) {
    int index = trouver_entree(cle_md5);
    if (index == -1) {
        return NULL;
    }
    return &dictionnaire[index];
}

int supprimer_peer(const char *cle_md5, const char *ip, int port) {
    int index = trouver_entree(cle_md5);
    if (index == -1) {
        return -1;
    }

    EntreeDictionnaire *entree = &dictionnaire[index];
    for (int i = 0; i < entree->nb_peers; i++) {
        if (strcmp(entree->peers[i].ip, ip) == 0 && entree->peers[i].port == port) {
            // Décaler les éléments suivants
            for (int j = i; j < entree->nb_peers - 1; j++) {
                entree->peers[j] = entree->peers[j + 1];
            }
            entree->nb_peers--;
            return 0;
        }
    }
    return -1;
}

int vider_peers(const char *cle_md5) {
    int index = trouver_entree(cle_md5);
    if (index == -1) {
        return -1;
    }

    dictionnaire[index].nb_peers = 0;
    return 0;
}

void afficher_peers(const char *cle_md5) {
    int index = trouver_entree(cle_md5);
    if (index == -1) {
        printf("Aucun peer trouvé pour le fichier: %s\n", cle_md5);
        return;
    }

    EntreeDictionnaire *entree = &dictionnaire[index];
    printf("Fichier (MD5): %s\n", cle_md5);
    printf("Nombre de peers: %d\n", entree->nb_peers);
    for (int i = 0; i < entree->nb_peers; i++) {
        printf("  Peer %d: %s:%d\n", i + 1, entree->peers[i].ip, entree->peers[i].port);
    }
}
