#ifndef __PEERS_H__
#define __PEERS_H__

#define TAILLE_IP 16  // "255.255.255.255\0"

typedef struct {
    char ip[TAILLE_IP];
    int port;
} Peer;

typedef struct PeerNode {
    Peer *peer;
    struct PeerNode *next;
} PeerNode;

// Pointeur vers le début de la liste chaînée
extern PeerNode *peer_list;

Peer *creer_peer(const char *ip, int port);

char* get_peer_ip(Peer *peer);

int get_peer_port(Peer *peer);

Peer* trouver_peer_par_ip(const char *ip);

#endif /* __PEERS_H__ */