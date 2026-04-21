#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "peers.h"

// ---------------- INTERNES ----------------
#define PEER_IP_BUCKETS 64

typedef struct PeerIpNode {
    Peer *peer;
    struct PeerIpNode *next;
} PeerIpNode;

// Liste globale des peers (API existante)
PeerNode *peer_list = NULL;

// Index hash pour accelerer la recherche par IP
static PeerIpNode *peer_ip_index[PEER_IP_BUCKETS] = {0};

// ---------------- OUTILS DE HACHAGE ----------------
// Meme idee que file.c: hash DJB2 simple et rapide.
static unsigned int hash_ip(const char *ip) {
    unsigned long h = 5381;
    int c;

    while ((c = *ip++) != 0) {
        h = ((h << 5) + h) + (unsigned)c;
    }

    return (unsigned int)(h % PEER_IP_BUCKETS);
}

// ---------------- INDEX IP (interne) ----------------
static void index_add_peer(Peer *peer) {
    unsigned int b = hash_ip(peer->ip);
    PeerIpNode *n = (PeerIpNode *)malloc(sizeof(PeerIpNode));
    if (n == NULL) return;

    n->peer = peer;
    n->next = peer_ip_index[b];
    peer_ip_index[b] = n;
}

static void index_remove_peer(Peer *peer) {
    unsigned int b = hash_ip(peer->ip);
    PeerIpNode *cur = peer_ip_index[b];
    PeerIpNode *prev = NULL;

    while (cur != NULL) {
        if (cur->peer == peer) {
            if (prev == NULL) {
                peer_ip_index[b] = cur->next;
            } else {
                prev->next = cur->next;
            }
            free(cur);
            return;
        }
        prev = cur;
        cur = cur->next;
    }
}

static void index_clear_all(void) {
    for (int b = 0; b < PEER_IP_BUCKETS; b++) {
        PeerIpNode *cur = peer_ip_index[b];
        while (cur != NULL) {
            PeerIpNode *next = cur->next;
            free(cur);
            cur = next;
        }
        peer_ip_index[b] = NULL;
    }
}

// ---------------- PUBLIQUE ----------------
Peer *creer_peer(const char *ip, int port) {
    if (ip == NULL || strlen(ip) >= TAILLE_IP) return NULL;
    if (port <= 0 || port > 65535) return NULL;

    Peer *new_peer = (Peer *)malloc(sizeof(Peer));
    if (new_peer == NULL) return NULL;

    strncpy(new_peer->ip, ip, TAILLE_IP - 1);
    new_peer->ip[TAILLE_IP - 1] = '\0';
    new_peer->port = port;

    PeerNode *new_node = (PeerNode *)malloc(sizeof(PeerNode));
    if (new_node == NULL) {
        free(new_peer);
        return NULL;
    }

    new_node->peer = new_peer;
    new_node->next = peer_list;
    peer_list = new_node;

    index_add_peer(new_peer);
    return new_peer;
}

PeerNode *find_peer_node(Peer *peer) {
    if (peer == NULL) return NULL;

    PeerNode *current = peer_list;
    while (current != NULL) {
        if (current->peer == peer) return current;
        current = current->next;
    }

    return NULL;
}

void delete_peer(Peer *peer) {
    if (peer == NULL) return;

    PeerNode *current = peer_list;
    PeerNode *prev = NULL;

    while (current != NULL) {
        if (current->peer == peer) {
            if (prev == NULL) {
                peer_list = current->next;
            } else {
                prev->next = current->next;
            }

            index_remove_peer(current->peer);
            free(current->peer);
            free(current);
            return;
        }

        prev = current;
        current = current->next;
    }
}

char *get_peer_ip(Peer *peer) {
    if (peer == NULL) return NULL;
    return peer->ip;
}

int get_peer_port(Peer *peer) {
    if (peer == NULL) return -1;
    return peer->port;
}

void delete_all_peers(void) {
    PeerNode *current = peer_list;

    while (current != NULL) {
        PeerNode *next = current->next;
        free(current->peer);
        free(current);
        current = next;
    }

    peer_list = NULL;
    index_clear_all();
}

int get_peers_count(void) {
    int count = 0;
    PeerNode *current = peer_list;

    while (current != NULL) {
        count++;
        current = current->next;
    }

    return count;
}

void print_all_peers(void) {
    PeerNode *current = peer_list;

    while (current != NULL) {
        printf("Peer IP: %s, Port: %d\n", current->peer->ip, current->peer->port);
        current = current->next;
    }
}

Peer *trouver_peer_par_ip(const char *ip) {
    if (ip == NULL) return NULL;

    // Recherche moyenne en O(1): on parcourt seulement le bucket de cette IP.
    unsigned int b = hash_ip(ip);
    PeerIpNode *cur = peer_ip_index[b];

    while (cur != NULL) {
        if (strcmp(cur->peer->ip, ip) == 0) return cur->peer;
        cur = cur->next;
    }

    return NULL;
}
