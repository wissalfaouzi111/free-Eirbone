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
#define LITSEN NULL
#define DEFAULT_PORT 8888



MessageP2P *parse_announce(char str[], MessageP2P *msg)
{
    char *token = strtok(str, " ");
    int i = 0;
    while (token != NULL)
    {
        if (i == 0)
        {
        }
        if (i == 1)
        {
            if (strcmp(token, "litsen") == 0)
            {
                // msg->port = LITSEN;
            }
            else
            {
                msg->port = DEFAULT_PORT;
            }
        }
        else if (i == 2)
        {
            if (strcmp(msg->commande, "announce") == 0 && (msg->port != DEFAULT_PORT))
            {
                msg->port = atoi(token);
            }
        }
        token = strtok(NULL, " ");
        i++;
    }
    return msg;
}

MessageP2P *add_message(char str[])
{

    MessageP2P *msg = malloc(sizeof(MessageP2P));
    char *token = strtok(str, " "); // Nous avons utlisé copilot pour comprendre le fonctionnement de strtok.
    strncpy(msg->commande, token, sizeof(msg->commande));
    if (strncmp(msg->commande, "announce", sizeof(msg->commande)) != 0)
    {
        return parse_announce(str, msg);
    }
    return msg;
}
