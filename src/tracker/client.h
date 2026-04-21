#ifndef __CLIENT_H__
#define __CLIENT_H__

#include <sys/select.h>
#include "peers.h"

typedef struct Client Client;

void client_system_init(void);

Client *create_client(int fd, const char *ip, int port);

Client *get_client(int fd);

void remove_client(int fd);

int client_count(void);

int client_max_fd(void);

int client_build_fdset(fd_set *set);

const char *client_get_ip(const Client *client);

int client_get_port(const Client *client);

int client_get_fd(const Client *client);

int client_get_error_count(const Client *client);

void client_reset_error_count(Client *client);

int client_inc_error_count(Client *client);

#endif /* __CLIENT_H__ */
