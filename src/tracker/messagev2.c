#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "messagev2.h"

#define BUFLEN 256
#define DEFAULT_PORT 6881
#define MAX_TOKENS 50

int is_number(const char *s){
    if (s == NULL || *s == '\0')
        return 0;

    for (int i = 0; s[i] != '\0'; i++)
    {
        if (s[i] < '0' || s[i] > '9')
            return 0;
    }
    return 1;
}

// lire une liste de clés
int lire_liste_cles(char **tokens, int i, int n,
                    char dest[][TAILLE_MD5], int *nb)
{
    if (i >= n || strcmp(tokens[i], "[") != 0)
        return -1;

    i++;

    while (i < n && strcmp(tokens[i], "]") != 0)
    {
        if (*nb >= MAX_LISTE)
            return -1;

        strncpy(dest[*nb], tokens[i], TAILLE_MD5 - 1);
        dest[*nb][TAILLE_MD5 - 1] = '\0';

        (*nb)++;
        i++;
    }

    if (i >= n)
        return -1;

    return i + 1;
}

// lire les fichiers seed de announce
int lire_seed_announce(char **tokens, int i, int n, MessageP2P *msg)
{
    if (i >= n || strcmp(tokens[i], "[") != 0)
        return -1;

    i++;

    while (i < n && strcmp(tokens[i], "]") != 0)
    {
        if (i + 3 >= n)
            return -1;

        if (msg->nb_seed >= MAX_LISTE)
            return -1;

        if (!is_number(tokens[i + 1]) || !is_number(tokens[i + 2]))
            return -1;

        strncpy(msg->liste_seed[msg->nb_seed].nom,
                tokens[i],
                sizeof(msg->liste_seed[msg->nb_seed].nom) - 1);
        msg->liste_seed[msg->nb_seed].nom[sizeof(msg->liste_seed[msg->nb_seed].nom) - 1] = '\0';

        msg->liste_seed[msg->nb_seed].taille = atol(tokens[i + 1]);
        msg->liste_seed[msg->nb_seed].taille_piece = atoi(tokens[i + 2]);

        strncpy(msg->liste_seed[msg->nb_seed].cle_md5,
                tokens[i + 3],
                TAILLE_MD5 - 1);
        msg->liste_seed[msg->nb_seed].cle_md5[TAILLE_MD5 - 1] = '\0';

        msg->nb_seed++;
        i += 4;
    }

    if (i >= n)
        return -1;

    return i + 1;
}

MessageP2P *parse_announce(char **tokens, int n, MessageP2P *msg)
{
    msg->port = DEFAULT_PORT;
    msg->nb_seed = 0;
    msg->nb_leech = 0;

    int i = 1;

    while (i < n)
    {
        if (strcmp(tokens[i], "listen") == 0)
        {
            if (i + 1 >= n || !is_number(tokens[i + 1]))
                return msg;

            msg->port = atoi(tokens[i + 1]);
            i += 2;
        }
        else if (strcmp(tokens[i], "seed") == 0)
        {
            int next = lire_seed_announce(tokens, i + 1, n, msg);
            if (next == -1)
                return msg;
            i = next;
        }
        else if (strcmp(tokens[i], "leech") == 0)
        {
            int next = lire_liste_cles(tokens, i + 1, n,
                                       msg->liste_leech_keys,
                                       &msg->nb_leech);
            if (next == -1)
                return msg;
            i = next;
        }
        else
        {
            i++;
        }
    }

    return msg;
}

MessageP2P *parse_update(char **tokens, int n, MessageP2P *msg)
{
    msg->nb_seed = 0;
    msg->nb_leech = 0;

    int i = 1;

    while (i < n)
    {
        if (strcmp(tokens[i], "seed") == 0)
        {
            int next = lire_liste_cles(tokens, i + 1, n,
                                       msg->liste_seed_keys,
                                       &msg->nb_seed);
            if (next == -1)
                return msg;
            i = next;
        }
        else if (strcmp(tokens[i], "leech") == 0)
        {
            int next = lire_liste_cles(tokens, i + 1, n,
                                       msg->liste_leech_keys,
                                       &msg->nb_leech);
            if (next == -1)
                return msg;
            i = next;
        }
        else
        {
            i++;
        }
    }

    return msg;
}

MessageP2P *add_message(char str[])
{
    MessageP2P *msg = malloc(sizeof(MessageP2P));
    if (msg == NULL)
        return NULL;

    memset(msg, 0, sizeof(MessageP2P));

    str[strcspn(str, "\r\n")] = '\0';

    char *tokens[MAX_TOKENS];
    int num_tokens = 0;

    char *token = strtok(str, " ");
    while (token != NULL && num_tokens < MAX_TOKENS)
    {
        tokens[num_tokens] = token;
        num_tokens++;
        token = strtok(NULL, " ");
    }

    if (num_tokens == 0)
        return msg;

    strncpy(msg->commande, tokens[0], sizeof(msg->commande) - 1);
    msg->commande[sizeof(msg->commande) - 1] = '\0';

    if (strcmp(msg->commande, "announce") == 0)
        return parse_announce(tokens, num_tokens, msg);

    if (strcmp(msg->commande, "update") == 0)
        return parse_update(tokens, num_tokens, msg);

    return msg;
}