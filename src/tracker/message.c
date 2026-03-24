#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "message.h"

#define BUFLEN 256
#define DEFAULT_PORT 6881
#define MAX_TOKENS 50

// Parsing de "announce" ou "update"
MessageP2P *parse_announce(char **tokens, int num_tokens, MessageP2P *msg) {
    int mode = 0; // 0 = mode normal, 1 = mode seed, 2 = mode leech
    msg->port = DEFAULT_PORT;
    msg->nb_seed = 0;
    msg->nb_leech = 0;

    for (int i = 1; i < num_tokens; i++) {

        // Gestion du port
        if (strcmp(tokens[i], "listenPort") == 0 || strcmp(tokens[i], "listen") == 0) {
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
                strcpy(msg->liste_seed[msg->nb_seed].nom, tokens[i]);
                msg->nb_seed++;
            } else if (mode == 2) { // leech
                strcpy(msg->liste_leech_keys[msg->nb_leech], tokens[i]);
                msg->nb_leech++;
            }
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
        else if (strcmp(msg->commande, "look") == 0) {
            return parse_look(tokens, num_tokens, msg);
        }
        else if (strcmp(msg->commande, "getfile") == 0 || strcmp(msg->commande, "interested") == 0 || strcmp(msg->commande, "have") == 0) {
            return parse_simple_hash(tokens, num_tokens, msg);
        }
        else if (strcmp(msg->commande, "getpieces") == 0) {
            return parse_getpieces(tokens, num_tokens, msg);
        }
        // TODO : la commande "data" n'est pas gérée car le strtok() casserait le binaire 
    }
    
    return msg;
}