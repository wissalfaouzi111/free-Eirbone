#ifndef __FILE_H__
#define __FILE_H__

#define NB_BUCKETS 100
#define TAILLE_MD5 33 // 32 caractères + le caractère de fin de chaîne '\0'

#include "peers.h"

typedef struct FilePeerNode {
    Peer *peer;
    struct FilePeerNode *next;
} FilePeerNode;

typedef struct {
    char nom[256];
    long taille;
    int taille_piece;      // Sera toujours 1024
    char cle_md5[TAILLE_MD5];

} FichierInfo;

FichierInfo *creer_fichier_info(const char *nom, long taille, int taille_piece, const char *cle_md5);

void liberer_fichier_info(FichierInfo *fichier);

FichierInfo* trouver_fichier_par_cle(const char *cle_md5);

char* get_name(const char *cle_md5);

long get_size(const char *cle_md5);

int get_piece_size(const char *cle_md5);

void add_peer_to_file(const char *cle_md5, Peer *peer);

int get_peers_for_file(const char *cle_md5, Peer *peers[]);

FichierInfo* trouver_fichier_par_nom(const char *nom);

char* generer_liste_peers_pour_fichier(const char *cle_md5);

void remove_peer_from_all_files(const char *ip);

#endif /* __FILE_H__ */