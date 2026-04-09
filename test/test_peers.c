#include <stdio.h>
#include <assert.h>
#include <string.h>
#include "../src/tracker/peers.h"

int main() {
    printf("--- TESTS PEERS ---\n");
    delete_all_peers();
    
    Peer *p1 = creer_peer("192.168.1.1", 6881);
    assert(p1 != NULL);
    assert(strcmp(get_peer_ip(p1), "192.168.1.1") == 0);
    assert(get_peer_port(p1) == 6881);
    
    Peer *p2 = creer_peer("10.0.0.1", 6882);
    assert(get_peers_count() == 2);

    delete_peer(p1);
    assert(get_peers_count() == 1);
    assert(find_peer_node(p2) != NULL);
    assert(find_peer_node(p1) == NULL);

    delete_all_peers();
    assert(get_peers_count() == 0);

    printf("✅ TOUS LES TESTS PEERS REUSSIS\n");
    return 0;
}