#include "file.h"
#include <stdlib.h>
#include <string.h>

FichierInfo *creer_fichier_info(const char *nom, long taille, int taille_piece, const char *cle_md5) {
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

    fichiers[nb_fichiers] = info;
    nb_fichiers++;

    return info;
}