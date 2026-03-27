#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h> 
#include "message.h"


// Tests rédigés par IA et relu/réadaptés à notre besoins.

// TEST 1 : Le cas parfait 
void test_announce_valide() {
    printf("Test 1: Announce parfait... ");
    
    char fausse_reception[] = "announce listen 6881 seed [ video.mp4 2048 1024 hash1234 ] leech [ hash5678 ]\n";
    
    MessageP2P *msg = add_message(fausse_reception);
    
    assert(msg != NULL);
    assert(strcmp(msg->commande, "announce") == 0);
    assert(msg->port == 6881);
    
    assert(msg->nb_seed == 1);
    assert(strcmp(msg->liste_seed[0].nom, "video.mp4") == 0);
    assert(msg->liste_seed[0].taille == 2048);
    assert(msg->liste_seed[0].taille_piece == 1024);
    assert(strcmp(msg->liste_seed[0].cle_md5, "hash1234") == 0);
    
    assert(msg->nb_leech == 1);
    assert(strcmp(msg->liste_leech_keys[0], "hash5678") == 0);
    
    free(msg);
    printf("OK!\n");
}

// TEST 2 : update 
void test_update_valide() {
    printf("Test 2: Update parfait... ");
    
    char fausse_reception[] = "update seed [ hash1111 hash2222 ] leech [ ]\n";
    MessageP2P *msg = add_message(fausse_reception);
    
    assert(msg != NULL);
    assert(strcmp(msg->commande, "update") == 0);
    assert(msg->nb_seed == 2);
    assert(strcmp(msg->liste_seed_keys[0], "hash1111") == 0);
    assert(msg->nb_leech == 0);
    
    free(msg);
    printf("OK!\n");
}

// TEST 3 : cas d'erreur 
void test_announce_malforme_lettres_dans_port() {
    printf("Test 3: Announce avec un faux port... ");
    
    char fausse_reception[] = "announce listen ABCD seed [ ]\n";
    MessageP2P *msg = add_message(fausse_reception);
    
    assert(msg != NULL);
    assert(strcmp(msg->commande, "announce") == 0);
    assert(msg->port == DEFAULT_PORT);
    
    free(msg);
    printf("OK!\n");
}

void test_announce_crochet_manquant() {
    printf("Test 4: Announce avec un crochet manquant... ");
    
    char fausse_reception[] = "announce listen 6881 seed [ video.mp4 2048 1024 hash1234 \n";
    MessageP2P *msg = add_message(fausse_reception);
    
    assert(msg != NULL);
    assert(msg->nb_seed == 0); 
    
    free(msg);
    printf("OK!\n");
}

// Main
int main() {
    
    test_announce_valide();
    test_update_valide();
    test_announce_malforme_lettres_dans_port();
    test_announce_crochet_manquant();
    
    printf("\n✅\n");
    return 0;
}