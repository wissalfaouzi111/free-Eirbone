#ifndef __MESSAGE_H__
#define __MESSAGE_H__

#define MAX_LISTE 50
#define TAILLE_MD5 33 // 32 caractères + le caractère de fin de chaîne '\0'
#define TAILLE_PIECE 1024

// Sous-structure pour Fichier
typedef struct {
    char nom[256];
    long taille;
    int taille_piece;      // Sera toujours 1024
    char cle_md5[TAILLE_MD5]; 
} FichierInfo;


typedef struct {
    // announce, look, data...
    char commande[20];

    // port (uniquement pour commande == "announce")
    int port;

    // clé du fichier concerné 
    char hash_cible[TAILLE_MD5];

    // listes de fichiers
    FichierInfo liste_seed[MAX_LISTE];
    int nb_seed;
    char liste_leech_keys[MAX_LISTE][TAILLE_MD5];
    int nb_leech;

    char buffermap_base64[1000]; 

    int pieces_demandees[MAX_LISTE];
    int nb_pieces_demandees;

    unsigned char charge_utile[TAILLE_PIECE];
    int taille_charge_utile; // Vaudra 1024, sauf avec le bourrage

} MessageP2P;

MessageP2P *add_message(char str[]);

#endif /* __MESSAGE_H__ */