#include <stdio.h>
#include <stdlib.h>
#include <strings.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <string.h>
#include "message.h"
#define BUFLEN 256
#define DEFAULT_PORT 8888
#define MAX_TOKENS 50

MessageP2P *parse_announce(char **tokens, int num_tokens, MessageP2P *msg)
{
    if (num_tokens > 1)
    {
        if (strcmp(tokens[1], "listen") == 0 || strcmp(tokens[1], "litsen") == 0)
        {
            msg->port = DEFAULT_PORT; // Valeur par défaut si rien d'autre n'est précisé
        }
        else
        {
            msg->port = DEFAULT_PORT;
        }

        if (num_tokens > 2 && msg->port != DEFAULT_PORT)
        {
            msg->port = atoi(tokens[2]);
        }
    }
    return msg;
}

MessageP2P *add_message(char str[])
{
    MessageP2P *msg = malloc(sizeof(MessageP2P));
    memset(msg, 0, sizeof(MessageP2P)); // Initialisation à zéro de la structure

    char *tokens[MAX_TOKENS];
    int num_tokens = 0;

    // Découpage de la chaîne en un tableau de tokens 
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
        msg->commande[sizeof(msg->commande) - 1] = '\0'; // Sécurité pour s'assurer qu'il y a un délimiteur

        // On appelle la fonction spécifique au type de message (et passage du tableau)
        if (strcmp(msg->commande, "announce") == 0)
        {
            return parse_announce(tokens, num_tokens, msg);
        }
        // else if (strcmp(msg->commande, "look") == 0) {
        //     return parse_look(tokens, num_tokens, msg);
        // }
    }
    
    return msg;
}
