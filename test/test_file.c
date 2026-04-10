#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include "../src/tracker/file.h"
#include "../src/tracker/peers.h"

int main() {    
    FichierInfo *f1 = creer_fichier_info("film.mp4", 1000, 1024, "MD5_FILM");
    assert(f1 != NULL);
    assert(strcmp(f1->nom, "film.mp4") == 0);

    FichierInfo *f_nom = trouver_fichier_par_nom("film.mp4");
    assert(f_nom != NULL);

    Peer *p1 = creer_peer("127.0.0.1", 6881);
    add_peer_to_file("MD5_FILM", p1);
    
    Peer *p_list[10];
    assert(get_peers_for_file("MD5_FILM", p_list) == 1);

    char *liste_formatee = generer_liste_peers_pour_fichier("MD5_FILM");
    assert(liste_formatee != NULL);
    assert(strcmp(liste_formatee, "[ 127.0.0.1:6881 ]") == 0);
    free(liste_formatee);

    printf("✅ TOUS LES TESTS FILE REUSSIS\n\n");
    return 0;
}