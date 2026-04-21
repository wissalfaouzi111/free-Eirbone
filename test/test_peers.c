#include <stdio.h>
#include <assert.h>
#include <string.h>
#include "../src/tracker/peers.h"

int main() {
    
    // Création
    Peer *p1 = creer_peer("192.168.1.1", 6881);
    assert(p1 != NULL);
    assert(strcmp(get_peer_ip(p1), "192.168.1.1") == 0);
    assert(get_peer_port(p1) == 6881);

    // Nouvelle fonction : trouver par IP
    Peer *found = trouver_peer_par_ip("192.168.1.1");
    assert(found == p1);
    
    assert(trouver_peer_par_ip("127.0.0.1") == NULL);

    printf("✅ TOUS LES TESTS PEERS REUSSIS\n\n");
    return 0;
}