#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include "../src/tracker/file.h"

int main() {
    printf("--- TESTS FILE ---\n");
    FichierInfo *f1 = creer_fichier_info("film.mp4", 1000, 1024, "MD5_FILM");
    assert(f1 != NULL);
    assert(strcmp(f1->nom, "film.mp4") == 0);

    // Test trouver par nom
    FichierInfo *f_nom = trouver_fichier_par_nom("film.mp4");
    assert(f_nom != NULL);
    assert(strcmp(f_nom->cle_md5, "MD5_FILM") == 0);

    // Test trouver par clé
    FichierInfo *f_cle = trouver_fichier_par_cle("MD5_FILM");
    assert(f_cle == f1);

    // Test ajout peer au fichier
    Peer *p1 = creer_peer("127.0.0.1", 8080);
    add_peer_to_file("MD5_FILM", p1);
    assert(get_peers_for_file("MD5_FILM", NULL) == 1);

    // Doublon : on ré-ajoute le même peer, le count doit rester 1
    add_peer_to_file("MD5_FILM", p1);
    assert(get_peers_for_file("MD5_FILM", NULL) == 1);

    printf("✅ TOUS LES TESTS FILE REUSSIS\n");
    return 0;
}