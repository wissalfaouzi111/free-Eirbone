#include <stddef.h>
#include <string.h>
#include "client.h"

#define CLIENT_BUFFER_SIZE 4096

struct Client {
    int fd;
    char ip[TAILLE_IP];
    int port;
    int active;
    int error_count;
    char in_buffer[CLIENT_BUFFER_SIZE];
    size_t in_len;
};

typedef struct {
    Client slots[FD_SETSIZE];
    int active_fds[FD_SETSIZE];
    int fd_pos[FD_SETSIZE];
    int active_count;
    int count;
    int max_fd;
} ClientTable;

static ClientTable g_clients;

static int is_valid_fd(int fd) {
    return fd >= 0 && fd < FD_SETSIZE;
}

static void recompute_max_fd(void) {
    g_clients.max_fd = -1;

    for (int i = 0; i < g_clients.active_count; i++) {
        int fd = g_clients.active_fds[i];
        if (fd > g_clients.max_fd) {
            g_clients.max_fd = fd;
        }
    }
}

void client_system_init(void) {
    memset(&g_clients, 0, sizeof(g_clients));
    g_clients.max_fd = -1;

    for (int fd = 0; fd < FD_SETSIZE; fd++) {
        g_clients.slots[fd].fd = -1;
        g_clients.fd_pos[fd] = -1;
    }
}

Client *create_client(int fd, const char *ip, int port) {
    if (ip == NULL) {
        return NULL;
    }
    if (!is_valid_fd(fd)) {
        return NULL;
    }
    if (g_clients.slots[fd].active) {
        return NULL;
    }
    if (strlen(ip) >= TAILLE_IP) {
        return NULL;
    }
    if (port <= 0 || port > 65535) {
        return NULL;
    }

    Client *client = &g_clients.slots[fd];
    client->fd = fd;
    strcpy(client->ip, ip);
    client->port = port;
    client->active = 1;
    client->error_count = 0;
    client->in_len = 0;
    client->in_buffer[0] = '\0';

    g_clients.fd_pos[fd] = g_clients.active_count;
    g_clients.active_fds[g_clients.active_count++] = fd;
    g_clients.count++;
    if (fd > g_clients.max_fd) {
        g_clients.max_fd = fd;
    }

    return client;
}

void remove_client(int fd) {
    if (!is_valid_fd(fd)) {
        return;
    }

    Client *client = &g_clients.slots[fd];
    if (!client->active) {
        return;
    }

    client->active = 0;
    client->fd = -1;
    client->ip[0] = '\0';
    client->port = 0;
    client->error_count = 0;
    client->in_len = 0;
    client->in_buffer[0] = '\0';

    // Suppression O(1) de la liste active : swap avec le dernier fd.
    int pos = g_clients.fd_pos[fd];
    if (pos >= 0) {
        int last_fd = g_clients.active_fds[g_clients.active_count - 1];
        g_clients.active_fds[pos] = last_fd;
        g_clients.fd_pos[last_fd] = pos;
        g_clients.active_count--;
        g_clients.fd_pos[fd] = -1;
    }

    g_clients.count--;

    if (fd == g_clients.max_fd) {
        recompute_max_fd();
    }
}

Client *get_client(int fd) {
    if (!is_valid_fd(fd)) {
        return NULL;
    }

    if (!g_clients.slots[fd].active) {
        return NULL;
    }

    return &g_clients.slots[fd];
}

int client_max_fd(void) {
    return g_clients.max_fd;
}

int client_list_fds(const int **fds) {
    if (fds == NULL) {
        return -1;
    }

    *fds = g_clients.active_fds;
    return g_clients.active_count;
}

const char *client_get_ip(const Client *client) {
    if (client == NULL) {
        return NULL;
    }

    return client->ip;
}

void client_reset_error_count(Client *client) {
    if (client == NULL) {
        return;
    }

    client->error_count = 0;
}

int client_inc_error_count(Client *client) {
    if (client == NULL) {
        return -1;
    }

    client->error_count++;
    return client->error_count;
}
