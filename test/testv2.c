#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include "message__.h"

MessageP2P *add_message(char str[]);

void afficher(MessageP2P *msg)
{
    printf("commande : %s\n", msg->commande);
    printf("port     : %d\n", msg->port);

    if (strcmp(msg->commande, "announce") == 0)
    {
        printf("SEED FILES (%d)\n", msg->nb_seed);
        for (int i = 0; i < msg->nb_seed; i++)
        {
            printf("  %s | taille=%ld | piece=%d | md5=%s\n",
                   msg->liste_seed[i].nom,
                   msg->liste_seed[i].taille,
                   msg->liste_seed[i].taille_piece,
                   msg->liste_seed[i].cle_md5);
        }
    }

    if (strcmp(msg->commande, "update") == 0)
    {
        printf("SEED KEYS (%d)\n", msg->nb_seed);
        for (int i = 0; i < msg->nb_seed; i++)
        {
            printf("  %s\n", msg->liste_seed_keys[i]);
        }
    }

    printf("LEECH KEYS (%d)\n", msg->nb_leech);
    for (int i = 0; i < msg->nb_leech; i++)
    {
        printf("  %s\n", msg->liste_leech_keys[i]);
    }

    printf("------------------------\n");
}

void tester(char *texte)
{
    char buffer[512];
    strcpy(buffer, texte); // strtok modifie la string

    printf("INPUT : %s\n", texte);

    MessageP2P *msg = add_message(buffer);

    if (msg == NULL)
    {
        printf("Erreur malloc\n");
        return;
    }

    afficher(msg);

    free(msg);
}

int main()
{
    tester("announce listen 6881 seed [ file.txt 2000 1024 abcdef1234567890abcdef1234567890 ] leech [ 11111111111111111111111111111111 ]");

    tester("update seed [ abcdef1234567890abcdef1234567890 99999999999999999999999999999999 ] leech [ 22222222222222222222222222222222 ]");

    return 0;
}