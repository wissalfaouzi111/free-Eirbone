#include "file.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

// ---------------- INTERNES ----------------
typedef struct PeerListNode {
    Peer *peer;
    struct PeerListNode *next;
} PeerListNode;

// Fichier et ses peers
typedef struct {
    FichierInfo *info;
    PeerListNode *peers_head;
    int nb_peers;
} FichierTracker;

// Pour la table de hachage par MD5
typedef struct HashNodeMD5 {
    FichierTracker *file_tracker;
    struct HashNodeMD5 *next;
} HashNodeMD5;

// Pour la table de hachage par NOM
typedef struct HashNodeName {
    FichierTracker *file_tracker;
    struct HashNodeName *next;
} HashNodeName;

static HashNodeMD5* table_md5[NB_BUCKETS] = {NULL};
static HashNodeName* table_name[NB_BUCKETS] = {NULL};

// ---------------- OUTILS DE HACHAGE (Algorithme DJB2) ----------------
// https://stackoverflow.com/questions/7666509/hash-function-for-string

unsigned int hash_string(const char *str) {
    unsigned long hash = 5381;
    int c;
    while ((c = *str++)) {
        hash = ((hash << 5) + hash) + c; // hash * 33 + c
    }
    return hash % NB_BUCKETS;
}

// Trouver le fichier à partir du MD5
static FichierTracker* get_tracker_by_md5(const char *cle_md5) {
    if (!cle_md5) return NULL;
    unsigned int idx = hash_string(cle_md5);
    HashNodeMD5 *node = table_md5[idx];
    while (node) {
        if (strcmp(node->file_tracker->info->cle_md5, cle_md5) == 0) return node->file_tracker;
        node = node->next;
    }
    return NULL;
}

// Trouver le fichier à partir du nom
static FichierTracker* get_tracker_by_name(const char *nom) {
    if (!nom) return NULL;
    unsigned int idx = hash_string(nom);
    HashNodeName *node = table_name[idx];
    while (node) {
        if (strcmp(node->file_tracker->info->nom, nom) == 0) return node->file_tracker;
        node = node->next;
    }
    return NULL;
}



// ---------------- PUBLIQUE ----------------

FichierInfo* creer_fichier_info(const char *nom, long taille, int taille_piece, const char *cle_md5) {
    // vérifie si le fichier existe déjà
    FichierTracker *ft = get_tracker_by_md5(cle_md5);
    if (ft) return ft->info;

    // Création en mémoire du fichier
    ft = malloc(sizeof(FichierTracker));
    ft->info = malloc(sizeof(FichierInfo));
    strncpy(ft->info->nom, nom, 255); ft->info->nom[255] = '\0';
    ft->info->taille = taille;
    ft->info->taille_piece = taille_piece;
    strncpy(ft->info->cle_md5, cle_md5, TAILLE_MD5 - 1); ft->info->cle_md5[TAILLE_MD5 - 1] = '\0';
    ft->peers_head = NULL;
    ft->nb_peers = 0;

    // Insertion dans la table par MD5
    unsigned int idx_md5 = hash_string(cle_md5);
    HashNodeMD5 *node_md5 = malloc(sizeof(HashNodeMD5));
    node_md5->file_tracker = ft;
    node_md5->next = table_md5[idx_md5];
    table_md5[idx_md5] = node_md5;

    // Insertion dans la table par NOM
    unsigned int idx_name = hash_string(nom);
    HashNodeName *node_name = malloc(sizeof(HashNodeName));
    node_name->file_tracker = ft;
    node_name->next = table_name[idx_name];
    table_name[idx_name] = node_name;

    return ft->info;
}

// ------ getters en temps constants
FichierInfo* trouver_fichier_par_nom(const char *nom) {
    FichierTracker *ft = get_tracker_by_name(nom);
    return ft ? ft->info : NULL;
}

FichierInfo* trouver_fichier_par_cle(const char *cle_md5) {
    FichierTracker *ft = get_tracker_by_md5(cle_md5);
    return ft ? ft->info : NULL;
}

int get_peers_for_file(const char *cle_md5, Peer **out_peers) {
    FichierTracker *ft = get_tracker_by_md5(cle_md5);
    if (!ft) return 0;
    
    if (out_peers) {
        PeerListNode *curr = ft->peers_head;
        int i = 0;
        while (curr && i < ft->nb_peers) {
            out_peers[i++] = curr->peer;
            curr = curr->next;
        }
    }
    return ft->nb_peers;
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

// ----------------------------------------

void add_peer_to_file(const char *cle_md5, Peer *peer) {
    FichierTracker *ft = get_tracker_by_md5(cle_md5);
    if (!ft || !peer) return;

    PeerListNode *curr = ft->peers_head;
    while (curr) {
        if (strcmp(get_peer_ip(curr->peer), get_peer_ip(peer)) == 0 && get_peer_port(curr->peer) == get_peer_port(peer)) {
            return;
        }
        curr = curr->next;
    }

    PeerListNode *new_node = malloc(sizeof(PeerListNode));
    new_node->peer = peer;
    new_node->next = ft->peers_head;
    ft->peers_head = new_node;
    ft->nb_peers++;
}

char* generer_liste_peers_pour_fichier(const char *cle_md5) {
    char *buffer = malloc(1024);
    if (!buffer) return NULL;
    size_t pos = 0;
    pos += snprintf(buffer + pos, 1024 - pos, "[");

    FichierTracker *ft = get_tracker_by_md5(cle_md5);
    if (ft) {
        PeerListNode *curr = ft->peers_head;
        while (curr) {
            int written = snprintf(buffer + pos, 1024 - pos, " %s:%d",
                                   get_peer_ip(curr->peer), get_peer_port(curr->peer));
            if (written > 0 && pos + (size_t)written < 1024) {
                pos += (size_t)written;
            }
            curr = curr->next;
        }
    }
    snprintf(buffer + pos, 1024 - pos, " ]");
    return buffer;
}