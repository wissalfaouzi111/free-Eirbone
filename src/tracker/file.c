#include "file.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

typedef struct HashNode {
    FichierInfo *info;
    struct HashNode *next;
} HashNode;

#define NB_BUCKETS 211

static HashNode *fichier_index[NB_BUCKETS];
static int nb_fichiers = 0;

// Fonction de hachage pour les clés MD5 : methode de djb2 adaptée pour les chaînes de caractères (https://stackoverflow.com/questions/7666509/hash-function-for-string)
static unsigned long hash_md5_key(const char *cle_md5) {
    unsigned long hash = 5381;
    const unsigned char *p = (const unsigned char *)cle_md5;

    while (*p != '\0') {
        hash = ((hash << 5) + hash) + *p;
        p++;
    }

    return hash % NB_BUCKETS;
}

static FichierInfo *find_info_in_bucket(const char *cle_md5, unsigned long bucket) {
    HashNode *node = fichier_index[bucket];

    while (node != NULL) {
        if (strcmp(node->info->cle_md5, cle_md5) == 0) {
            return node->info;
        }
        node = node->next;
    }

    return NULL;
}

FichierInfo *creer_fichier_info(const char *nom, long taille, int taille_piece, const char *cle_md5) {
    if (nom == NULL || cle_md5 == NULL) {
        return NULL;
    }

    unsigned long bucket = hash_md5_key(cle_md5);
    FichierInfo *existing = find_info_in_bucket(cle_md5, bucket);

    if (existing != NULL) {
        strncpy(existing->nom, nom, sizeof(existing->nom) - 1);
        existing->nom[sizeof(existing->nom) - 1] = '\0';
        existing->taille = taille;
        existing->taille_piece = taille_piece;
        return existing;
    }

    if (nb_fichiers >= MAX_FICHIERS) {
        return NULL;
    }

    FichierInfo *info = malloc(sizeof(FichierInfo));
    if (info == NULL) {
        return NULL;
    }
    strncpy(info->nom, nom, sizeof(info->nom) - 1);
    info->nom[sizeof(info->nom) - 1] = '\0';
    info->taille = taille;
    info->taille_piece = taille_piece;
    strncpy(info->cle_md5, cle_md5, sizeof(info->cle_md5) - 1);
    info->cle_md5[sizeof(info->cle_md5) - 1] = '\0';
    info->peers = NULL; 

    HashNode *node = malloc(sizeof(HashNode));
    if (node == NULL) {
        free(info);
        return NULL;
    }

    node->info = info;
    node->next = fichier_index[bucket];
    fichier_index[bucket] = node;

    nb_fichiers++;

    return info;
}

void liberer_fichier_info(FichierInfo *fichier) {
    if (fichier == NULL) {
        return;
    }

    FilePeerNode *curr_peer = fichier->peers;
    while (curr_peer != NULL) {
        FilePeerNode *next_peer = curr_peer->next;
        free(curr_peer);
        curr_peer = next_peer;
    }
    fichier->peers = NULL;

    unsigned long bucket = hash_md5_key(fichier->cle_md5);
    HashNode *curr = fichier_index[bucket];
    HashNode *prev = NULL;

    while (curr != NULL) {
        if (curr->info == fichier) {
            if (prev == NULL) {
                fichier_index[bucket] = curr->next;
            } else {
                prev->next = curr->next;
            }
            free(curr->info);
            free(curr);
            if (nb_fichiers > 0) {
                nb_fichiers--;
            }
            return;
        }
        prev = curr;
        curr = curr->next;
    }
}

FichierInfo* trouver_fichier_par_cle(const char *cle_md5) {
    if (cle_md5 == NULL) {
        return NULL;
    }

    unsigned long bucket = hash_md5_key(cle_md5);
    return find_info_in_bucket(cle_md5, bucket);
}

char* get_name(const char *cle_md5) {
    FichierInfo *fichier = trouver_fichier_par_cle(cle_md5);
    return (fichier != NULL) ? fichier->nom : NULL;
}

long get_size(const char *cle_md5) {
    FichierInfo *fichier = trouver_fichier_par_cle(cle_md5);
    return (fichier != NULL) ? fichier->taille : -1;
}

int get_piece_size(const char *cle_md5) {
    FichierInfo *fichier = trouver_fichier_par_cle(cle_md5);
    return (fichier != NULL) ? fichier->taille_piece : -1;
}

void add_peer_to_file(const char *cle_md5, Peer *peer) {
    if (cle_md5 == NULL || peer == NULL) {
        return;
    }

    FichierInfo *fichier = trouver_fichier_par_cle(cle_md5);
    if (fichier == NULL) {
        return;
    }

    // Vérifier que le peer n'est pas déjà dans la liste
    FilePeerNode *current = fichier->peers;
    while (current != NULL) {
        if (current->peer == peer) {
            return;  
        }
        current = current->next;
    }

    // Créer un nouveau nœud pour la liste chaînée
    FilePeerNode *new_node = malloc(sizeof(FilePeerNode));
    if (new_node == NULL) {
        return;
    }

    new_node->peer = peer;
    new_node->next = fichier->peers;
    fichier->peers = new_node;  // Ajouter au début de la liste
}


int get_peers_for_file(const char *cle_md5, Peer *peers[]) {
    if (cle_md5 == NULL) {
        return 0;
    }

    FichierInfo *fichier = trouver_fichier_par_cle(cle_md5);
    if (fichier == NULL) {
        return 0;
    }

    int count = 0;
    FilePeerNode *current = fichier->peers;

    // Si peers est NULL, on compte juste les peers
    if (peers == NULL) {
        while (current != NULL) {
            count++;
            current = current->next;
        }
        return count;
    }

    current = fichier->peers;
    while (current != NULL) {
        peers[count] = current->peer;
        count++;
        current = current->next;
    }

    return count;
}

FichierInfo* trouver_fichier_par_nom(const char *nom) {
    if (nom == NULL) return NULL;
    for (int i = 0; i < NB_BUCKETS; i++) {
        HashNode *node = fichier_index[i];
        while (node != NULL) {
            if (strcmp(node->info->nom, nom) == 0) {
                return node->info;
            }
            node = node->next;
        }
    }
    return NULL;
}

char* generer_liste_peers_pour_fichier(const char *cle_md5) {
    char *buffer = malloc(1024);
    if (buffer == NULL) return NULL;
    
    strcpy(buffer, "[");
    
    FichierInfo *fichier = trouver_fichier_par_cle(cle_md5);
    if (fichier == NULL) {
        free(buffer);
        return NULL;
    }
    
    FilePeerNode *current = fichier->peers;
    while (current != NULL) {
        char temp[64];
        snprintf(temp, sizeof(temp), " %s:%d", get_peer_ip(current->peer), get_peer_port(current->peer));
        strcat(buffer, temp);
        current = current->next;
    }
    
    strcat(buffer, " ]");
    return buffer;
}