#include <string.h>
#include "client.h"

static int is_valid_fd(int fd) {
    return fd >= 0 && fd < FD_SETSIZE;
}

static void recompute_max_fd(ClientTable *table) {
    table->max_fd = -1;

    for (int fd = FD_SETSIZE - 1; fd >= 0; fd--) {
        if (table->slots[fd].active) {
            table->max_fd = fd;
            break;
        }
    }
}

void client_table_init(ClientTable *table) {
    if (table == NULL) {
        return;
    }

    memset(table, 0, sizeof(*table));
    table->max_fd = -1;

    for (int fd = 0; fd < FD_SETSIZE; fd++) {
        table->slots[fd].fd = -1;
    }
}

int client_table_add(ClientTable *table, int fd, const char *ip, int port) {
    if (table == NULL || ip == NULL) {
        return -1;
    }
    if (!is_valid_fd(fd)) {
        return -1;
    }
    if (table->slots[fd].active) {
        return -1;
    }
    if (strlen(ip) >= TAILLE_IP) {
        return -1;
    }
    if (port <= 0 || port > 65535) {
        return -1;
    }

    Client *client = &table->slots[fd];
    client->fd = fd;
    strcpy(client->ip, ip);
    client->port = port;
    client->active = 1;
    client->in_len = 0;
    client->in_buffer[0] = '\0';

    table->count++;
    if (fd > table->max_fd) {
        table->max_fd = fd;
    }

    return 0;
}

void client_table_remove(ClientTable *table, int fd) {
    if (table == NULL || !is_valid_fd(fd)) {
        return;
    }

    Client *client = &table->slots[fd];
    if (!client->active) {
        return;
    }

    client->active = 0;
    client->fd = -1;
    client->ip[0] = '\0';
    client->port = 0;
    client->in_len = 0;
    client->in_buffer[0] = '\0';

    table->count--;

    if (fd == table->max_fd) {
        recompute_max_fd(table);
    }
}

Client *client_table_get(ClientTable *table, int fd) {
    if (table == NULL || !is_valid_fd(fd)) {
        return NULL;
    }

    if (!table->slots[fd].active) {
        return NULL;
    }

    return &table->slots[fd];
}

int client_table_count(const ClientTable *table) {
    if (table == NULL) {
        return 0;
    }

    return table->count;
}

int client_table_max_fd(const ClientTable *table) {
    if (table == NULL) {
        return -1;
    }

    return table->max_fd;
}

int client_table_build_fdset(const ClientTable *table, fd_set *set) {
    if (table == NULL || set == NULL) {
        return -1;
    }

    FD_ZERO(set);
    for (int fd = 0; fd < FD_SETSIZE; fd++) {
        if (table->slots[fd].active) {
            FD_SET(fd, set);
        }
    }

    return table->max_fd;
}
