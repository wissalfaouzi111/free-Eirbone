#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "message.h"

#define BUFLEN 256
#define DEFAULT_PORT 6881
#define MAX_TOKENS 50




int is_number(const char *s) {
    if (*s == '\0') return 0;
    for (int i = 0; s[i]; i++) {
        if (s[i] < '0' || s[i] > '9') return 0;
    }
    return 1;
}


// Parsing de "announce" ou "update"
MessageP2P *parse_announce(char **tokens, int num_tokens, MessageP2P *msg) {
    int mode = 0; // 0 = mode normal, 1 = mode seed, 2 = mode leech
    msg->port = DEFAULT_PORT;
    msg->nb_seed = 0;
    msg->nb_leech = 0;

    for (int i = 1; i < num_tokens; i++) {

        // Gestion du port
        if (strcmp(tokens[i], "listen") == 0) {
            if (i + 1 < num_tokens) {
                msg->port = atoi(tokens[i + 1]);
                i++;
            }
        } 
        // Mode
        else if (strcmp(tokens[i], "seed") == 0) {
            mode = 1;
        } 
        else if (strcmp(tokens[i], "leech") == 0) {
            mode = 2;
        } 
        // Ignorer les crochets
        else if (strcmp(tokens[i], "[") == 0 || strcmp(tokens[i], "]") == 0) {
            continue; 
        } 
        // Nom de fichier ou hash
        else {
            if (mode == 1) { // seed
                strncpy(msg->liste_seed[msg->nb_seed].nom, tokens[i],sizeof(msg->liste_seed[msg->nb_seed].nom) - 1);
                msg->liste_seed[msg->nb_seed].nom[sizeof(msg->liste_seed[msg->nb_seed].nom) - 1] = '\0';
                msg->nb_seed++;
            } else if (mode == 2) { // leech
                strncpy(msg->liste_leech_keys[msg->nb_leech], tokens[i],sizeof(msg->liste_leech_keys[msg->nb_leech]) - 1);
                msg->liste_leech_keys[msg->nb_leech][sizeof(msg->liste_leech_keys[msg->nb_leech]) - 1] = '\0';
                msg->nb_leech++;
            }
        }
    }
    return msg;
}


// parsing de look
MessageP2P *parse_look(char **tokens, int num_tokens, MessageP2P *msg) {
    for (int i = 1; i < num_tokens; i++) {
        if (strcmp(tokens[i], "[") != 0 && strcmp(tokens[i], "]") != 0) {
            strcpy(msg->hash_cible, tokens[i]);
            break; // TODO : gérer le cas où il y a plusieurs criteres
        }
    }
    return msg;
}


// FONCTION PRINCIPALE
MessageP2P *add_message(char str[])
{
    MessageP2P *msg = malloc(sizeof(MessageP2P));
    memset(msg, 0, sizeof(MessageP2P));

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

    if (num_tokens > 0)
    {
        strncpy(msg->commande, tokens[0], sizeof(msg->commande) - 1);
        msg->commande[sizeof(msg->commande) - 1] = '\0';

        // Parsing selon la commande
        if (strcmp(msg->commande, "announce") == 0 || strcmp(msg->commande, "update") == 0) {
            return parse_announce(tokens, num_tokens, msg);
        }
        // else if (strcmp(msg->commande, "look") == 0) {
        //     return parse_look(tokens, num_tokens, msg);
        // }
        // else if (strcmp(msg->commande, "getfile") == 0 || strcmp(msg->commande, "interested") == 0 || strcmp(msg->commande, "have") == 0) {
        //     return parse_simple_hash(tokens, num_tokens, msg);
        // }
        // else if (strcmp(msg->commande, "getpieces") == 0) {
        //     return parse_getpieces(tokens, num_tokens, msg);
        // }
        // TODO : la commande "data" n'est pas gérée car le strtok() casserait le binaire 
    }
    
    return msg;
}



MessageP2P *parse_update(char **tokens, int num_tokens, MessageP2P *msg){
    int mode = 0; 

    msg->nb_seed = 0;
    msg->nb_leech = 0;

    for (int i = 1; i < num_tokens; i++)
    {
        if (strcmp(tokens[i], "seed") == 0)
        {
            mode = 1;
        }
        else if (strcmp(tokens[i], "leech") == 0)
        {
            mode = 2;
        }
        else if (strcmp(tokens[i], "[") == 0 || strcmp(tokens[i], "]") == 0)
        {
            continue;
        }
        else
        {
            if (mode == 1)
            {
                strncpy(msg->liste_seed_keys[msg->nb_seed], tokens[i],
                        sizeof(msg->liste_seed_keys[msg->nb_seed]) - 1);
                msg->liste_seed_keys[msg->nb_seed][sizeof(msg->liste_seed_keys[msg->nb_seed]) - 1] = '\0';
                msg->nb_seed++;
            }
            else if (mode == 2)
            {
                strncpy(msg->liste_leech_keys[msg->nb_leech], tokens[i],
                        sizeof(msg->liste_leech_keys[msg->nb_leech]) - 1);
                msg->liste_leech_keys[msg->nb_leech][sizeof(msg->liste_leech_keys[msg->nb_leech]) - 1] = '\0';
                msg->nb_leech++;
            }
        }
    }

    return msg;
}