#ifndef __CLIENT_H__
#define __CLIENT_H__

#include <stddef.h>
#include <sys/select.h>
#include "peers.h"

#define CLIENT_BUFFER_SIZE 4096

/*
    Structure représentant un client connecté au tracker
*/
typedef struct {
    int fd;
    char ip[TAILLE_IP];
    int port;
    int active;
    char in_buffer[CLIENT_BUFFER_SIZE];
    size_t in_len;
} Client;

/*
    Table de clients connectés, indexée par des descripteurs de fichiers (fd)
*/
typedef struct {
    Client slots[FD_SETSIZE];
    int count;
    int max_fd;
} ClientTable;

/*
    Initialise la table de clients
*/
void client_table_init(ClientTable *table);

/*
    Ajoute un client à la table
*/
int client_table_add(ClientTable *table, int fd, const char *ip, int port);

/*
    Retire un client de la table
*/
void client_table_remove(ClientTable *table, int fd);

/*
    Récupère un client de la table par son descripteur de fichier
*/
Client *client_table_get(ClientTable *table, int fd);

/*
    Renvoie le nombre de clients dans la table
*/
int client_table_count(const ClientTable *table);

/*
    Renvoie le plus grand descripteur de fichier dans la table
*/
int client_table_max_fd(const ClientTable *table);

/*
    Construit un ensemble de descripteurs de fichiers à partir de la table
*/
int client_table_build_fdset(const ClientTable *table, fd_set *set);

#endif /* __CLIENT_H__ */
