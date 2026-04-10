#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include "../src/tracker/message.h"

int main() {

    // 1. TEST ANNOUNCE
    printf("Test 1: Announce complet... ");
    char msg_announce[] = "announce listen 6881 seed [ video.mp4 2048 1024 hash1234 ] leech [ hash5678 ]\n";
    MessageP2P *m1 = add_message(msg_announce);
    assert(m1 != NULL);
    assert(strcmp(m1->commande, "announce") == 0);
    assert(m1->port == 6881);
    assert(m1->nb_seed == 1);
    assert(strcmp(m1->liste_seed[0].nom, "video.mp4") == 0);
    assert(strcmp(m1->liste_seed[0].cle_md5, "hash1234") == 0);
    assert(m1->nb_leech == 1);
    assert(strcmp(m1->liste_leech_keys[0], "hash5678") == 0);
    free(m1);
    printf("OK!\n");

    // 2. TEST LOOK
    printf("Test 2: Look... ");
    char msg_look[] = "look [ video.mp4 ]\n";
    MessageP2P *m2 = add_message(msg_look);
    assert(m2 != NULL);
    assert(strcmp(m2->commande, "look") == 0);
    assert(strcmp(m2->hash_cible, "video.mp4") == 0);
    free(m2);
    printf("OK!\n");

    // 3. TEST GETFILE
    printf("Test 3: Getfile... ");
    char msg_getfile[] = "getfile hash1234\n";
    MessageP2P *m3 = add_message(msg_getfile);
    assert(m3 != NULL);
    assert(strcmp(m3->commande, "getfile") == 0);
    assert(strcmp(m3->hash_cible, "hash1234") == 0);
    free(m3);
    printf("OK!\n");

    // 4. TEST ROLLBACK SUR ANNOUNCE MALFORMÉ
    printf("Test 4: Announce malformé (Rollback)... ");
    char msg_malforme[] = "announce listen 6881 seed [ video.mp4 2048 1024 hash1234 \n";
    MessageP2P *m4 = add_message(msg_malforme);
    assert(m4 != NULL);
    assert(m4->nb_seed == 0);
    free(m4);
    printf("OK!\n");

    printf("✅ TOUS LES TESTS PARSING REUSSIS\n\n");
    return 0;
}