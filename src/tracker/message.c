#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "message.h"

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
int lire_liste_cles(char **tokens, int i, int n, char dest[][TAILLE_MD5], int *nb)
{
    if (i >= n || strcmp(tokens[i], "[") != 0)
        return -1;

    i++;
    int initial_count = *nb; 

    while (i < n && strcmp(tokens[i], "]") != 0)
    {
        if (*nb >= MAX_LISTE) {
            *nb = initial_count;
            return -1;
        }

        strncpy(dest[*nb], tokens[i], TAILLE_MD5 - 1);
        dest[*nb][TAILLE_MD5 - 1] = '\0';

        (*nb)++;
        i++;
    }

    if (i >= n) {
        *nb = initial_count; 
        return -1;
    }

    return i + 1;
}

// lire les fichiers seed de announce
int lire_seed_announce(char **tokens, int i, int n, MessageP2P *msg)
{
    if (i >= n || strcmp(tokens[i], "[") != 0)
        return -1;

    i++;
    
    int initial_seed_count = msg->nb_seed; 

    while (i < n && strcmp(tokens[i], "]") != 0)
    {
        if (i + 3 >= n) {
            msg->nb_seed = initial_seed_count; 
            return -1;
        }

        if (msg->nb_seed >= MAX_LISTE) {
            msg->nb_seed = initial_seed_count; 
            return -1;
        }

        if (!is_number(tokens[i + 1]) || !is_number(tokens[i + 2])) {
            msg->nb_seed = initial_seed_count;
            return -1;
        }

        strncpy(msg->liste_seed[msg->nb_seed].nom, tokens[i], sizeof(msg->liste_seed[msg->nb_seed].nom) - 1);
        msg->liste_seed[msg->nb_seed].nom[sizeof(msg->liste_seed[msg->nb_seed].nom) - 1] = '\0';

        msg->liste_seed[msg->nb_seed].taille = atol(tokens[i + 1]);
        msg->liste_seed[msg->nb_seed].taille_piece = atoi(tokens[i + 2]);

        strncpy(msg->liste_seed[msg->nb_seed].cle_md5, tokens[i + 3], TAILLE_MD5 - 1);
        msg->liste_seed[msg->nb_seed].cle_md5[TAILLE_MD5 - 1] = '\0';

        msg->nb_seed++;
        i += 4;
    }

    if (i >= n) {
        msg->nb_seed = initial_seed_count;
        return -1;
    }

    return i + 1; 
}

// Parsing de "announce"
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

// parsing de look
MessageP2P *parse_look(char **tokens, int n, MessageP2P *msg)
{
    // On parcourt les mots pour trouver le critère (qui n'est pas un crochet)
    for (int i = 1; i < n; i++)
    {
        if (strcmp(tokens[i], "[") != 0 && strcmp(tokens[i], "]") != 0)
        {
            strncpy(msg->hash_cible, tokens[i], sizeof(msg->hash_cible) - 1);
            msg->hash_cible[sizeof(msg->hash_cible) - 1] = '\0';
            break; 
        }
    }
    return msg;
}

// Parsing de getfile
MessageP2P *parse_getfile(char **tokens, int n, MessageP2P *msg)
{
    if (n > 1)
    {
        strncpy(msg->hash_cible, tokens[1], TAILLE_MD5 - 1);
        msg->hash_cible[TAILLE_MD5 - 1] = '\0';
    }
    return msg;
}

// FONCTION PRINCIPALE
MessageP2P *add_message(char str[])
{
    if (str == NULL)
        return NULL;

    MessageP2P *msg = malloc(sizeof(MessageP2P));
    if (msg == NULL)
        return NULL;

    memset(msg, 0, sizeof(MessageP2P));

    str[strcspn(str, "\r\n")] = '\0'; // remplacer le caractère de fin\r\n par \0

    char *tokens[MAX_TOKENS];
    int num_tokens = 0;

    // Découpage de la chaîne en mots
    char *token = strtok(str, " ");
    while (token != NULL && num_tokens < MAX_TOKENS)
    {
        tokens[num_tokens] = token;
        num_tokens++;
        token = strtok(NULL, " ");
    }

    if (num_tokens == 0) return msg;

    strncpy(msg->commande, tokens[0], sizeof(msg->commande) - 1);
    msg->commande[sizeof(msg->commande) - 1] = '\0';

    // Parsing selon la commande
    if (strcmp(msg->commande, "announce") == 0)
        return parse_announce(tokens, num_tokens, msg);

    if (strcmp(msg->commande, "look") == 0)
        return parse_look(tokens, num_tokens, msg);
    
    if (strcmp(msg->commande, "getfile") == 0)
        return parse_getfile(tokens, num_tokens, msg);

    return msg;
}

