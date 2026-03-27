#ifndef __FILE_H__
#define __FILE_H__
#define MAX_FICHIERS 100
#define TAILLE_MD5 33 // 32 caractères + le caractère de fin de chaîne '\0'


typedef struct {
    char nom[256];
    long taille;
    int taille_piece;      // Sera toujours 1024
    char cle_md5[TAILLE_MD5]; 
} FichierInfo;

FichierInfo* fichiers[MAX_FICHIERS]; // Tableau global pour stocker les fichiers
int nb_fichiers = 0; // Compteur pour le nombre de fichiers stockés

FichierInfo *creer_fichier_info(const char *nom, long taille, int taille_piece, const char *cle_md5);

void liberer_fichier_info(FichierInfo *fichier);

FichierInfo* trouver_fichier_par_cle(const char *cle_md5);

char* get_name(const char *cle_md5);

long get_size(const char *cle_md5);

int get_piece_size(const char *cle_md5);

#endif /* __FILE_H__ */