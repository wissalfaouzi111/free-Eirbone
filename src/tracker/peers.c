#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "peers.h"

PeerNode *peer_list = NULL; // Tete de la liste chaînée des peers

Peer *creer_peer(const char *ip, int port) {
    if (ip == NULL) {
        return NULL;
    }
    
    if (strlen(ip) >= TAILLE_IP) {
        return NULL;
    }
    
    if (port <= 0 || port > 65535) {
        return NULL;
    }
    
    Peer *new_peer = (Peer *)malloc(sizeof(Peer));
    if (new_peer == NULL) {
        return NULL;
    }
    
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
    
    return new_peer;
}

PeerNode* find_peer_node(Peer *peer) {
    if (peer == NULL) {
        return NULL;
    }
    
    PeerNode *current = peer_list;
    while (current != NULL) {
        if (current->peer == peer) {
            return current;
        }
        current = current->next;
    }
    
    return NULL;
}

void delete_peer(Peer *peer) {
    if (peer == NULL) {
        return;
    }
    
    PeerNode *current = peer_list;
    PeerNode *prev = NULL;
    
    while (current != NULL) {
        if (current->peer == peer) {
            if (prev == NULL) {
                peer_list = current->next;
            } else {
                prev->next = current->next;
            }
            
            free(current->peer);
            free(current);
            return;
        }
        
        prev = current;
        current = current->next;
    }
}


char* get_peer_ip(Peer *peer) {
    if (peer == NULL) {
        return NULL;
    }
    return peer->ip;
}


int get_peer_port(Peer *peer) {
    if (peer == NULL) {
        return -1;
    }
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


Peer* trouver_peer_par_ip(const char *ip) {
    if (ip == NULL) return NULL;
    
    PeerNode *courant = peer_list; 
    while (courant != NULL) {
        if (strcmp(get_peer_ip(courant->peer), ip) == 0) {
            return courant->peer;
        }
        courant = courant->next;
    }
    return NULL;
}